package net.Alexxiconify.alexxAutoWarn.utils;

import java.util.Collection;

/**
 * Utility class for string manipulation, particularly for tab completion.
 */
public class StringUtil {

 private StringUtil() {
  // Private constructor to prevent instantiation
 }

 /**
  * Copies all partial matches from a collection of originals to a collection of completions.
  * This is useful for tab completion where you want to suggest entries that start with the user's input.
  *
  * @param token       The user's current input (the string to match against).
  * @param originals   The collection of original strings to search through.
  * @param completions The collection to which matching strings will be added.
  */
 public static void copyPartialMatches(String token, Iterable<String> originals, Collection<String> completions) {
  for (String string : originals) {
   if (startsWithIgnoreCase(string, token)) {
    completions.add(string);
   }
  }
 }

 /**
  * Checks if a string starts with a prefix, ignoring case.
  *
  * @param string The string to check.
  * @param prefix The prefix to check for.
  * @return True if the string starts with the prefix (ignoring case), false otherwise.
  */
 public static boolean startsWithIgnoreCase(String string, String prefix) {
  if (string.length() < prefix.length()) {
   return false;
  }
  return string.substring(0, prefix.length()).equalsIgnoreCase(prefix);
 }
}