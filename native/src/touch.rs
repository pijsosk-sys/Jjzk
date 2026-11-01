// Touch controls for Horizon on Android. This file is the body of the game's
// `touch` module (game.rs includes it for Android builds), so it reaches the
// game's own input paths: the keys a keyboard would press, the mouse buttons,
// the grabbed-pointer view turn and the pad's analog move vector.
//
// In game (no screen open):
//   left part of the screen   a floating stick that walks (analog, like a pad's)
//   anywhere else             drag to look; tap to use or place; hold to mine or attack
//   hotbar                    tap a slot to select it
//   buttons                   jump, sneak and sprint (toggles), attack, inventory,
//                             drop, chat, camera and pause
// In menus, the title screen and inventories: one finger is the mouse (tap to
// click, drag to drag), a second finger scrolls, and two buttons give Back
// (Escape) and the on-screen keyboard.

use super::event_loop::EventLoopCtl;
use super::{Game, Screen};
use horizon_client::hud::{HudBatch, rgba};
use std::cell::RefCell;
use std::time::{Duration, Instant};
use winit::dpi::PhysicalPosition;
use winit::event::{
    DeviceId, ElementState, MouseButton, MouseScrollDelta, Touch, TouchPhase, WindowEvent,
};
use winit::keyboard::KeyCode;
use winit::window::WindowId;

/// A touch on the look area held this long without moving starts mining.
const HOLD: Duration = Duration::from_millis(280);
/// GUI pixels a finger may drift and still count as a tap.
const TAP_SLOP: f32 = 5.0;
/// The stick's reach in GUI pixels: a finger this far from where it landed is full tilt.
const STICK_RADIUS: f32 = 24.0;
/// Mouse counts per physical pixel of look drag (vanilla's sensitivity applies on top).
const LOOK_SCALE: f64 = 0.9;
/// GUI pixels of second-finger drag per scroll line in menus.
const SCROLL_STEP: f32 = 12.0;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
enum Control {
    Jump,
    Sneak,
    Sprint,
    Attack,
    Inventory,
    Drop,
    Chat,
    Camera,
    Pause,
    Back,
    Keyboard,
}

impl Control {
    fn label(self) -> &'static str {
        match self {
            Control::Jump => "^",
            Control::Sneak => "v",
            Control::Sprint => ">>",
            Control::Attack => "X",
            Control::Inventory => "...",
            Control::Drop => "Q",
            Control::Chat => "T",
            Control::Camera => "F5",
            Control::Pause => "II",
            Control::Back => "<",
            Control::Keyboard => "Aa",
        }
    }
}

#[derive(Clone, Copy, PartialEq, Debug)]
enum Role {
    /// The walking stick, centred where the finger landed.
    Stick {
        centre: [f32; 2],
    },
    /// Turning the view; a tap uses, a hold mines.
    Look {
        mining: bool,
    },
    Control(Control),
    /// Fingers on the hotbar select a slot and do nothing more.
    Hotbar,
    /// The menu pointer.
    Pointer,
    /// A second finger in a menu: the scroll wheel.
    Scroll,
    /// Started in another mode (a screen opened or closed under the finger).
    Ignored,
}

#[derive(Clone, Copy, Debug)]
struct Finger {
    id: u64,
    role: Role,
    /// GUI pixels where it landed and where it is.
    start: [f32; 2],
    at: [f32; 2],
    /// Physical pixels at the last event, for the look delta.
    last: PhysicalPosition<f64>,
    down: Instant,
    travelled: f32,
}

#[derive(Default)]
struct State {
    fingers: Vec<Finger>,
    /// The walking stick's tilt, x right and y up, -1 to 1.
    stick: Option<[f32; 2]>,
    sneaking: bool,
    sprinting: bool,
    /// A tap's use button goes up once a game tick has seen it down.
    release_use_at: Option<u64>,
}

thread_local! {
    static STATE: RefCell<State> = RefCell::new(State::default());
}

/// The stick's tilt while it is held (`Game::pad_move` reads it).
pub(super) fn stick() -> Option<[f32; 2]> {
    STATE.with(|s| s.borrow().stick)
}

/// The controls' places on screen, in GUI pixels: `[x, y, width, height]`.
fn controls(w: f32, h: f32, in_menu: bool) -> Vec<(Control, [f32; 4])> {
    if in_menu {
        return vec![
            (Control::Back, [w - 44.0, 4.0, 18.0, 18.0]),
            (Control::Keyboard, [w - 22.0, 4.0, 18.0, 18.0]),
        ];
    }
    let centre = w / 2.0;
    vec![
        (Control::Jump, [w - 46.0, h - 64.0, 34.0, 34.0]),
        (Control::Sneak, [w - 82.0, h - 46.0, 28.0, 28.0]),
        (Control::Attack, [w - 82.0, h - 82.0, 28.0, 28.0]),
        (Control::Sprint, [14.0, h - 122.0, 24.0, 24.0]),
        (Control::Inventory, [centre + 94.0, h - 22.0, 22.0, 22.0]),
        (Control::Drop, [centre - 91.0 - 25.0, h - 22.0, 22.0, 22.0]),
        (Control::Camera, [w - 66.0, 4.0, 18.0, 18.0]),
        (Control::Chat, [w - 44.0, 4.0, 18.0, 18.0]),
        (Control::Pause, [w - 22.0, 4.0, 18.0, 18.0]),
    ]
}

fn inside([x, y]: [f32; 2], [rx, ry, rw, rh]: [f32; 4]) -> bool {
    x >= rx && x < rx + rw && y >= ry && y < ry + rh
}

/// The hotbar slot under a point (`Hud.extractItemHotbar`'s 182 by 22 bar).
fn hotbar_slot([x, y]: [f32; 2], w: f32, h: f32) -> Option<usize> {
    let left = (w / 2.0).floor() - 91.0;
    (x >= left && x < left + 182.0 && y >= h - 22.0).then(|| (((x - left) / 20.0) as usize).min(8))
}

/// The walking stick takes fingers landing on the left 40% of the screen,
/// below the top third.
fn stick_zone([x, y]: [f32; 2], w: f32, h: f32) -> bool {
    x < w * 0.4 && y > h / 3.0
}

impl Game<'_> {
    /// `WindowEvent::Touch`, before anything else sees the event.
    pub(super) fn touch_event(
        &mut self,
        event_loop: &dyn EventLoopCtl,
        id: WindowId,
        touch: Touch,
    ) {
        let Some(batch) = self.gui_scale() else {
            return;
        };
        let (w, h, scale) = (batch.width, batch.height, batch.scale);
        let at = [
            touch.location.x as f32 / scale,
            touch.location.y as f32 / scale,
        ];
        let in_menu = self.pad_in_menu();
        self.last_input = Instant::now();
        match touch.phase {
            TouchPhase::Started => self.touch_started(event_loop, id, &touch, at, w, h, in_menu),
            TouchPhase::Moved => self.touch_moved(event_loop, id, &touch, at),
            TouchPhase::Ended | TouchPhase::Cancelled => {
                self.touch_ended(event_loop, id, &touch, at, touch.phase == TouchPhase::Ended)
            }
        }
    }

    fn touch_started(
        &mut self,
        event_loop: &dyn EventLoopCtl,
        id: WindowId,
        touch: &Touch,
        at: [f32; 2],
        w: f32,
        h: f32,
        in_menu: bool,
    ) {
        let (has_pointer, has_stick) = STATE.with(|s| {
            let s = s.borrow();
            (
                s.fingers.iter().any(|f| f.role == Role::Pointer),
                s.fingers
                    .iter()
                    .any(|f| matches!(f.role, Role::Stick { .. })),
            )
        });
        let control = controls(w, h, in_menu)
            .into_iter()
            .find(|(_, rect)| inside(at, *rect))
            .map(|(c, _)| c);
        let role = if let Some(control) = control {
            Role::Control(control)
        } else if in_menu {
            if has_pointer {
                Role::Scroll
            } else {
                Role::Pointer
            }
        } else if hotbar_slot(at, w, h).is_some() {
            Role::Hotbar
        } else if !has_stick && stick_zone(at, w, h) {
            Role::Stick { centre: at }
        } else {
            Role::Look { mining: false }
        };
        STATE.with(|s| {
            s.borrow_mut().fingers.push(Finger {
                id: touch.id,
                role,
                start: at,
                at,
                last: touch.location,
                down: Instant::now(),
                travelled: 0.0,
            })
        });
        match role {
            Role::Control(control) => self.control_pressed(event_loop, control),
            Role::Pointer => {
                self.pointer_to(event_loop, id, touch.location);
                self.pointer_button(event_loop, id, ElementState::Pressed);
            }
            Role::Hotbar => {
                if let Some(slot) = hotbar_slot(at, w, h)
                    && self.screen == Screen::Playing
                {
                    self.selected = slot;
                }
            }
            Role::Stick { .. } => STATE.with(|s| s.borrow_mut().stick = Some([0.0, 0.0])),
            Role::Look { .. } | Role::Scroll | Role::Ignored => {}
        }
    }

    fn touch_moved(
        &mut self,
        event_loop: &dyn EventLoopCtl,
        id: WindowId,
        touch: &Touch,
        at: [f32; 2],
    ) {
        let Some(finger) = STATE.with(|s| {
            let mut s = s.borrow_mut();
            let finger = s.fingers.iter_mut().find(|f| f.id == touch.id)?;
            let before = finger.at;
            finger.travelled += ((at[0] - before[0]).powi(2) + (at[1] - before[1]).powi(2)).sqrt();
            finger.at = at;
            let previous = *finger;
            finger.last = touch.location;
            Some((previous, before))
        }) else {
            return;
        };
        let (finger, before) = finger;
        match finger.role {
            Role::Stick { centre } => {
                let (dx, dy) = (
                    (at[0] - centre[0]) / STICK_RADIUS,
                    (at[1] - centre[1]) / STICK_RADIUS,
                );
                let len = (dx * dx + dy * dy).sqrt().max(1.0);
                // Up the screen is forward: the stick's y is up positive.
                STATE.with(|s| s.borrow_mut().stick = Some([dx / len, -dy / len]));
            }
            Role::Look { .. } => {
                if self.pad_in_menu() {
                    return;
                }
                let dx = (touch.location.x - finger.last.x) * LOOK_SCALE;
                let dy = (touch.location.y - finger.last.y) * LOOK_SCALE;
                // MouseHandler.onMove with the pointer grabbed (Android counts it as grabbed).
                self.mouse.on_move(0.0, 0.0, dx, dy, true, true);
            }
            Role::Pointer => self.pointer_to(event_loop, id, touch.location),
            Role::Scroll => {
                let lines = (before[1] - at[1]) / SCROLL_STEP;
                if lines != 0.0 {
                    self.handle_window_event(
                        event_loop,
                        id,
                        WindowEvent::MouseWheel {
                            device_id: DeviceId::dummy(),
                            delta: MouseScrollDelta::LineDelta(0.0, -lines),
                            phase: TouchPhase::Moved,
                        },
                    );
                }
            }
            Role::Control(_) | Role::Hotbar | Role::Ignored => {}
        }
    }

    fn touch_ended(
        &mut self,
        event_loop: &dyn EventLoopCtl,
        id: WindowId,
        touch: &Touch,
        at: [f32; 2],
        completed: bool,
    ) {
        let Some(finger) = STATE.with(|s| {
            let mut s = s.borrow_mut();
            let i = s.fingers.iter().position(|f| f.id == touch.id)?;
            Some(s.fingers.remove(i))
        }) else {
            return;
        };
        match finger.role {
            Role::Stick { .. } => STATE.with(|s| s.borrow_mut().stick = None),
            Role::Look { mining: true } => self.touch_mouse(MouseButton::Left, false),
            Role::Look { mining: false } => {
                let tap = completed && finger.travelled < TAP_SLOP && finger.down.elapsed() < HOLD;
                if tap && !self.pad_in_menu() {
                    // A tap uses the item or the block (key.use), released after a tick.
                    self.touch_mouse(MouseButton::Right, true);
                    let ticks = self.ticks;
                    STATE.with(|s| s.borrow_mut().release_use_at = Some(ticks + 1));
                }
            }
            Role::Control(control) => self.control_released(control),
            Role::Pointer => {
                self.pointer_to(event_loop, id, touch.location);
                self.pointer_button(event_loop, id, ElementState::Released);
            }
            Role::Scroll | Role::Hotbar | Role::Ignored => {}
        }
        let _ = at;
    }

    /// Every frame, from the HUD: start mining under a held finger, let a
    /// tap's use go, and draw the controls.
    pub(super) fn draw_touch_controls(&mut self, batch: &mut HudBatch) {
        let in_menu = self.pad_in_menu();
        self.touch_frame(in_menu);
        let (w, h) = (batch.width, batch.height);
        let atlas = &self.gui.atlas;
        let (fingers, stick, sneaking, sprinting) = STATE.with(|s| {
            let s = s.borrow();
            (s.fingers.clone(), s.stick, s.sneaking, s.sprinting)
        });
        let held = |c: Control| fingers.iter().any(|f| f.role == Role::Control(c));
        for (control, [x, y, cw, ch]) in controls(w, h, in_menu) {
            let on = held(control)
                || (control == Control::Sneak && sneaking)
                || (control == Control::Sprint && sprinting);
            let fill = if on {
                rgba(255, 255, 255, 110)
            } else {
                rgba(0, 0, 0, 90)
            };
            batch.fill(atlas, x, y, cw, ch, fill);
            // A thin light border, so the buttons show on dark ground too.
            let edge = rgba(255, 255, 255, 70);
            batch.fill(atlas, x, y, cw, 1.0, edge);
            batch.fill(atlas, x, y + ch - 1.0, cw, 1.0, edge);
            batch.fill(atlas, x, y, 1.0, ch, edge);
            batch.fill(atlas, x + cw - 1.0, y, 1.0, ch, edge);
            let label = control.label();
            let tw = HudBatch::text_width(atlas, label);
            batch.text(
                atlas,
                label,
                (x + (cw - tw) / 2.0).floor(),
                (y + (ch - 8.0) / 2.0).floor(),
                rgba(255, 255, 255, 230),
                true,
            );
        }
        if in_menu {
            return;
        }
        // The stick: its ring where the finger landed (or its resting place), the knob at the tilt.
        let centre = fingers
            .iter()
            .find_map(|f| match f.role {
                Role::Stick { centre } => Some(centre),
                _ => None,
            })
            .unwrap_or([48.0, h - 64.0]);
        let r = STICK_RADIUS;
        batch.fill(
            atlas,
            centre[0] - r,
            centre[1] - r,
            r * 2.0,
            r * 2.0,
            rgba(0, 0, 0, 50),
        );
        let tilt = stick.unwrap_or([0.0, 0.0]);
        let knob = [centre[0] + tilt[0] * r, centre[1] - tilt[1] * r];
        batch.fill(
            atlas,
            knob[0] - 8.0,
            knob[1] - 8.0,
            16.0,
            16.0,
            rgba(255, 255, 255, 120),
        );
    }

    fn touch_frame(&mut self, in_menu: bool) {
        let ticks = self.ticks;
        let (start_mining, release_use) = STATE.with(|s| {
            let mut s = s.borrow_mut();
            let mut start = false;
            if !in_menu {
                for f in &mut s.fingers {
                    if f.role == (Role::Look { mining: false })
                        && f.travelled < TAP_SLOP
                        && f.down.elapsed() >= HOLD
                    {
                        f.role = Role::Look { mining: true };
                        start = true;
                    }
                }
            }
            let release = s.release_use_at.is_some_and(|at| ticks >= at);
            if release {
                s.release_use_at = None;
            }
            (start, release)
        });
        if start_mining {
            // key.attack held: mining a block, or attacking what is in front.
            self.touch_mouse(MouseButton::Left, true);
        }
        if release_use {
            self.touch_mouse(MouseButton::Right, false);
        }
    }

    /// The app went to the background: let go of everything held.
    pub(super) fn touch_reset(&mut self) {
        let state = STATE.with(|s| std::mem::take(&mut *s.borrow_mut()));
        for f in &state.fingers {
            match f.role {
                Role::Look { mining: true } => self.touch_mouse(MouseButton::Left, false),
                Role::Control(c @ (Control::Jump | Control::Attack)) => self.control_released(c),
                _ => {}
            }
        }
        if state.release_use_at.is_some() {
            self.touch_mouse(MouseButton::Right, false);
        }
        // Sneak and sprint stay as they were: the next touch session starts fresh.
        if state.sneaking {
            let _ = self.mapped_key(KeyCode::ShiftLeft, false, false);
        }
        if state.sprinting {
            let _ = self.mapped_key(KeyCode::ControlLeft, false, false);
        }
    }

    fn control_pressed(&mut self, event_loop: &dyn EventLoopCtl, control: Control) {
        let result = match control {
            Control::Jump => self.mapped_key(KeyCode::Space, true, false),
            Control::Attack => {
                self.touch_mouse(MouseButton::Left, true);
                Ok(())
            }
            Control::Sneak => {
                let on = STATE.with(|s| {
                    let mut s = s.borrow_mut();
                    s.sneaking = !s.sneaking;
                    s.sneaking
                });
                self.mapped_key(KeyCode::ShiftLeft, on, false)
            }
            Control::Sprint => {
                let on = STATE.with(|s| {
                    let mut s = s.borrow_mut();
                    s.sprinting = !s.sprinting;
                    s.sprinting
                });
                self.mapped_key(KeyCode::ControlLeft, on, false)
            }
            Control::Inventory => self.tap_key(KeyCode::KeyE),
            Control::Drop => self.tap_key(KeyCode::KeyQ),
            Control::Chat => self.tap_key(KeyCode::KeyT),
            Control::Camera => self.tap_key(KeyCode::F5),
            Control::Pause => self.tap_key(KeyCode::Escape),
            Control::Back => self.pad_back(),
            Control::Keyboard => {
                // The soft keyboard for a field the game did not ask it for
                // (the game shows it by itself for its text boxes).
                if let Some(window) = &self.window {
                    window.set_ime_allowed(!self.ime_allowed);
                    self.ime_allowed = !self.ime_allowed;
                }
                Ok(())
            }
        };
        let _ = event_loop;
        if let Err(e) = result {
            log::warn!("Horizon: touch {control:?}: {e}");
        }
    }

    fn control_released(&mut self, control: Control) {
        let result = match control {
            Control::Jump => self.mapped_key(KeyCode::Space, false, false),
            Control::Attack => {
                self.touch_mouse(MouseButton::Left, false);
                Ok(())
            }
            _ => Ok(()),
        };
        if let Err(e) = result {
            log::warn!("Horizon: touch {control:?}: {e}");
        }
    }

    /// A key pressed and let go, as a quick tap on a keyboard.
    fn tap_key(&mut self, key: KeyCode) -> horizon_client::Result<()> {
        self.mapped_key(key, true, false)?;
        self.mapped_key(key, false, false)
    }

    /// key.attack or key.use in game, as the mouse buttons do.
    fn touch_mouse(&mut self, button: MouseButton, pressed: bool) {
        if !pressed || (self.title.is_none() && self.screen == Screen::Playing) {
            if let Err(e) = self.play_mouse(button, pressed) {
                log::warn!("Horizon: touch {button:?}: {e}");
            }
        }
    }

    /// The menu pointer to a finger, as the window's cursor.
    fn pointer_to(
        &mut self,
        event_loop: &dyn EventLoopCtl,
        id: WindowId,
        at: PhysicalPosition<f64>,
    ) {
        self.handle_window_event(
            event_loop,
            id,
            WindowEvent::CursorMoved {
                device_id: DeviceId::dummy(),
                position: at,
            },
        );
    }

    fn pointer_button(&mut self, event_loop: &dyn EventLoopCtl, id: WindowId, state: ElementState) {
        self.handle_window_event(
            event_loop,
            id,
            WindowEvent::MouseInput {
                device_id: DeviceId::dummy(),
                state,
                button: MouseButton::Left,
            },
        );
    }
}
