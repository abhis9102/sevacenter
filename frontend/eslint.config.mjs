import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const config = [
  ...nextVitals,
  ...nextTs,
  {
    rules: {
      // All API data is untrusted (a devotee name can be "<script>…"). React escapes text;
      // this is the escape hatch that would bypass it, so it is banned outright.
      "react/no-danger": "error",
      "no-restricted-syntax": [
        "error",
        {
          selector: "MemberExpression[property.name=/^(innerHTML|outerHTML)$/]",
          message: "Never write HTML from strings: render text through React.",
        },
        {
          selector: "CallExpression[callee.property.name='insertAdjacentHTML']",
          message: "Never write HTML from strings: render text through React.",
        },
      ],
      "no-eval": "error",
      "no-implied-eval": "error",
      "no-new-func": "error",
      // No PII or tokens in the browser console.
      "no-console": "error",
    },
  },
  {
    ignores: [".next/**", "node_modules/**", ".test-build/**", "next-env.d.ts"],
  },
];

export default config;
