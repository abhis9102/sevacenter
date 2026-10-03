/**
 * Razorpay Checkout loader (ADR 0013). The script is injected by our own (nonce'd) code, which
 * the page's CSP ('strict-dynamic') trusts; the donate page's CSP also allows Razorpay's frames.
 * The browser never decides the amount: the server created the order, and confirms the payment
 * with Razorpay itself before anything is recorded.
 */
const CHECKOUT_SRC = "https://checkout.razorpay.com/v1/checkout.js";

export interface CheckoutSuccess {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

interface CheckoutOptions {
  key: string;
  order_id: string;
  amount: number;
  currency: "INR";
  name: string;
  description?: string;
  prefill?: { name?: string };
  handler: (response: CheckoutSuccess) => void;
  modal?: { ondismiss?: () => void };
}

interface RazorpayInstance {
  open(): void;
}

type RazorpayCtor = new (options: CheckoutOptions) => RazorpayInstance;

let loading: Promise<RazorpayCtor> | null = null;

export function loadCheckout(): Promise<RazorpayCtor> {
  const existing = (window as unknown as { Razorpay?: RazorpayCtor }).Razorpay;
  if (existing) {
    return Promise.resolve(existing);
  }
  loading ??= new Promise<RazorpayCtor>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = CHECKOUT_SRC;
    script.async = true;
    script.onload = () => {
      const ctor = (window as unknown as { Razorpay?: RazorpayCtor }).Razorpay;
      if (ctor) {
        resolve(ctor);
      } else {
        reject(new Error("checkout_unavailable"));
      }
    };
    script.onerror = () => {
      loading = null;
      reject(new Error("checkout_unavailable"));
    };
    document.head.appendChild(script);
  });
  return loading;
}

/** "501" or "501.50" (as the API expects: rupees, at most 2 decimals, Rs 1 - Rs 10,00,000). */
export function validAmount(raw: string): boolean {
  const v = raw.trim();
  if (!/^(?:[1-9]\d{0,6}(?:\.\d{1,2})?)$/.test(v)) {
    return false;
  }
  return Number(v) <= 1_000_000;
}
