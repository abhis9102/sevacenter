import Link from "next/link";

export default function NotFound() {
  return (
    <main className="mx-auto flex max-w-md flex-col gap-3 px-4 py-24 text-center">
      <h1 className="text-2xl">Page not found</h1>
      <p className="text-muted">That page doesn&apos;t exist.</p>
      <Link href="/devotees" className="text-primary-strong underline">
        Go to devotees
      </Link>
    </main>
  );
}
