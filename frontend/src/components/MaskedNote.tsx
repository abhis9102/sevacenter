import { Alert } from "@/components/ui";

/** Shown wherever the server returned masked devotee data (ADR 0010). */
export function MaskedNote() {
  return (
    <Alert tone="info" title="Contact details hidden for your role">
      As a member you see names, city and state in full; phone numbers show only the last 4 digits and emails only
      their first letter. Address, pincode and date of birth are hidden. Search works on names only.
    </Alert>
  );
}
