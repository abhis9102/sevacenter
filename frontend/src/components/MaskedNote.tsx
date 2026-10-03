"use client";

import { useLanguage } from "@/components/LanguageProvider";
import { Alert } from "@/components/ui";

/** Shown wherever the server returned masked devotee data (ADR 0010). */
export function MaskedNote() {
  const { t } = useLanguage();
  return (
    <Alert tone="info" title={t.devotees.maskedNoteTitle}>
      {t.devotees.maskedNoteBody}
    </Alert>
  );
}
