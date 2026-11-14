import js from "@eslint/js";
import globals from "globals";

export default [
  { ignores: ["node_modules/**", ".tools/**", "**/target/**"] },
  {
    files: ["frontend/js/**/*.js", "tests/frontend/**/*.js"],
    ...js.configs.recommended,
    languageOptions: { ecmaVersion: 2023, sourceType: "module" },
    rules: {
      ...js.configs.recommended.rules,
      "no-empty": ["error", { allowEmptyCatch: true }],
      "no-unused-vars": ["error", { caughtErrors: "none" }],
    },
  },
  { files: ["frontend/js/**/*.js"], languageOptions: { globals: globals.browser } },
  { files: ["tests/frontend/**/*.js"], languageOptions: { globals: globals.node } },
];
