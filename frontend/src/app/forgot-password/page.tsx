"use client";

import Link from "next/link";

import { AuthFrame } from "@/components/AuthFrame";
import { useLanguage } from "@/components/LanguageProvider";
import { Alert } from "@/components/ui";

/**
 * Password reset links are issued by a trust admin (Staff → Reset password link), not requested
 * here: without email delivery, a page that hands out a link would hand it to whoever asked.
 * Self-service reset returns once the platform sends email.
 */
export default function ForgotPasswordPage() {
  const { t } = useLanguage();
  return (
    <AuthFrame title={t.auth.forgot.title}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted">{t.auth.forgot.subtitle}</p>
        <Alert tone="info">{t.auth.forgot.instructions}</Alert>
        <div className="pt-2 text-center">
          <Link href="/login" className="text-xs text-primary-strong hover:underline">
            ← {t.auth.forgot.backToLogin}
          </Link>
        </div>
      </div>
    </AuthFrame>
  );
}
