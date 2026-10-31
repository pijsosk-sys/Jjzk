package rex;

import java.util.Locale;

/** Chat routing for Overlord, independent of the menu and of Baritone's command registry. */
public final class OverlordInput {
   public record Command(String action, String rest) {
   }

   private OverlordInput() {
   }

   /** Recognises ".overlord ...", "#overlord ...", ".rex overlord ..." and the common misspelling. */
   public static Command parse(String text) {
      if (text == null) {
         return null;
      }
      String cleaned = text.replace(' ', ' ').replace('​', ' ').replace("﻿", "").strip();
      if (cleaned.isEmpty()) {
         return null;
      }
      String[] words = cleaned.split("\\s+");
      String prefix = words[0].toLowerCase(Locale.ROOT);
      int action = 1;
      if ((prefix.equals(".rex") || prefix.equals(".one")) && words.length > 1 && words[1].equalsIgnoreCase("overlord")) {
         action = 2;
      } else if (!prefix.equals("#overlord") && !prefix.equals("#overloard") && !prefix.equals(".overloard")
         && !prefix.equals(".overlord") && !prefix.equals("/rexoverlord")) {
         return null;
      }
      String verb = words.length > action ? words[action].toLowerCase(Locale.ROOT) : "status";
      StringBuilder rest = new StringBuilder();
      for (int i = action + 1; i < words.length; i++) {
         rest.append(rest.length() == 0 ? "" : " ").append(words[i]);
      }
      return new Command(verb, rest.toString());
   }
}
