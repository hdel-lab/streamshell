// Lint for the page scripts StreamShell injects into YouTube (exported by JsScriptsExportTest). They are classic
// scripts (no modules), run in the page's own browser context, and talk to the app only through the message channels.
import js from "@eslint/js";
import globals from "globals";
export default [
  js.configs.recommended,
  {
    files: ["**/*.js"],
    languageOptions: { ecmaVersion: 2022, sourceType: "script", globals: { ...globals.browser, streamShellSponsor: "readonly", streamShellNav: "readonly" } },
    rules: { "no-empty": ["error", { allowEmptyCatch: true }], "no-unused-vars": ["error", { args: "none", caughtErrors: "none" }] },
  },
];
