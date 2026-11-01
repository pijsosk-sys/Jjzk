// The Android entry point, included into horizon-client's `android.rs` (the
// library's root, next to the game module). It does what the desktop `main`
// does, with the folders and arguments of the app instead of a command line.
//
// Folders, under the app's internal storage (the launcher activity makes them):
//   horizon/            the game folder: options.txt, saves/, logs/, crash-reports/
//   horizon/resources/  the prepared game files imported from a computer
//                       (`horizon-resources.json` at its top)
//   horizon/launch-args.txt  extra arguments from the launcher, one a line

use winit::platform::android::activity::AndroidApp;

/// The mod API level, kept in the library as in the desktop program.
static MOD_API_LEVEL: [u8; 27] = horizon_mods::api_level::MARK;

/// The desktop game's main thread has 8 MB of stack (splitscreen builds a
/// `Game` deep in the event loop); `android_main`'s thread has less.
const GAME_STACK: usize = 16 * 1024 * 1024;

#[unsafe(no_mangle)]
fn android_main(app: AndroidApp) {
    std::hint::black_box(&MOD_API_LEVEL);
    let base = app
        .internal_data_path()
        .unwrap_or_else(|| std::path::PathBuf::from("/data/local/tmp"));
    let home = base.join("horizon");
    let resources = home.join("resources");
    let _ = std::fs::create_dir_all(&home);
    // The game folder is the working directory, as the launcher starts it on a computer.
    let _ = std::env::set_current_dir(&home);
    horizon_threads::configure_allocator();
    horizon_client::telemetry::mark_start();
    horizon_client::logging::set_game_home(home.clone());
    horizon_client::logging::init(&home, "Render thread");
    log::info!("Horizon {} on Android", horizon_client::crash::version());
    horizon_client::crash::install(home.clone(), "client");

    let mut args = vec![
        "--resources".to_owned(),
        resources.display().to_string(),
        "--fullscreen".to_owned(),
    ];
    if let Ok(extra) = std::fs::read_to_string(home.join("launch-args.txt")) {
        args.extend(
            extra
                .lines()
                .map(str::trim)
                .filter(|l| !l.is_empty() && !l.starts_with('#'))
                .map(str::to_owned),
        );
    }
    let _ = game::ANDROID_ARGS.set(args);
    let _ = game::ANDROID_APP.set(app);

    let game_home = home.clone();
    let result = std::thread::Builder::new()
        .name("Render thread".into())
        .stack_size(GAME_STACK)
        .spawn(move || match game::run() {
            Ok(()) => true,
            Err(error) => {
                log::error!("Horizon: {error}");
                horizon_client::logging::flush();
                if !error.is::<game::UsageError>() {
                    horizon_client::crash::report_error(&game_home, "client", &*error);
                }
                false
            }
        })
        .map(|thread| thread.join());
    match result {
        Ok(Ok(true)) => {}
        Ok(Ok(false)) => log::error!("Horizon: the game stopped with an error"),
        Ok(Err(_)) => log::error!("Horizon: the game thread panicked"),
        Err(e) => log::error!("Horizon: could not start the game thread: {e}"),
    }
    horizon_client::logging::flush();
    // winit builds one event loop a process: the next start must be a new process.
    std::process::exit(0);
}
