"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { api } from "@/components/apiClient";
import { useLanguage } from "@/components/LanguageProvider";
import { useMe } from "@/components/Session";
import { Alert, Button, Card, Spinner } from "@/components/ui";
import { ApiError, describeError } from "@/lib/errors";
import { hasRole, type DonationFund, type DonationItem, type DonationPage, type DonationSummary, type DonationMode, type ReceiptDetail, DONATION_MODES } from "@/lib/types";

const PAGE_SIZE = 25;

export default function DonationsPage() {
  const me = useMe();
  const { t } = useLanguage();

  const [page, setPage] = useState(0);
  const [data, setData] = useState<DonationPage | null>(null);
  const [summary, setSummary] = useState<DonationSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [modeFilter, setModeFilter] = useState<string>("ALL");

  // Record Donation Modal
  const [showRecordModal, setShowRecordModal] = useState(false);
  const [recordDonor, setRecordDonor] = useState("");
  const [recordAmount, setRecordAmount] = useState("");
  const [recordMode, setRecordMode] = useState<DonationMode>("CASH");
  const [recordPurpose, setRecordPurpose] = useState("");
  const [recordFund, setRecordFund] = useState("");
  const [funds, setFunds] = useState<DonationFund[]>([]);
  const [newFund, setNewFund] = useState("");
  const [fundError, setFundError] = useState<string | null>(null);
  const [recordReference, setRecordReference] = useState("");
  const [recordDate, setRecordDate] = useState(() => new Date().toISOString().split("T")[0]);
  const [recording, setRecording] = useState(false);
  const [recordError, setRecordError] = useState<string | null>(null);

  // Reversal Modal
  const [reversingDonation, setReversingDonation] = useState<DonationItem | null>(null);
  const [reversalReason, setReversalReason] = useState("");
  const [reversing, setReversing] = useState(false);
  const [reversalError, setReversalError] = useState<string | null>(null);

  // Receipt Modal
  const [viewingReceipt, setViewingReceipt] = useState<ReceiptDetail | null>(null);
  const [, setReceiptLoading] = useState(false);
  const [receiptError, setReceiptError] = useState<string | null>(null);
  const [issueForDonation, setIssueForDonation] = useState<DonationItem | null>(null);
  const [issuePan, setIssuePan] = useState("");
  const [issueAddress, setIssueAddress] = useState("");
  const [issuing, setIssuing] = useState(false);

  const canRecord = hasRole(me.role, "LEADER");
  const canReverse = hasRole(me.role, "TRUST_ADMIN");

  async function saveFund(id: number | null, name: string, active: boolean) {
    setFundError(null);
    try {
      await api.request<DonationFund>(id === null ? "/donation-funds" : `/donation-funds/${id}`, {
        method: id === null ? "POST" : "PUT",
        json: { name: name.trim(), active },
      });
      if (id === null) setNewFund("");
      setFunds(await api.get<DonationFund[]>("/donation-funds"));
    } catch (err) {
      setFundError(err instanceof ApiError && err.fields.name ? err.fields.name : describeError(err));
    }
  }

  const loadData = useCallback(() => {
    setLoading(true);
    const params = new URLSearchParams({
      page: String(page),
      size: String(PAGE_SIZE),
    });
    if (from) params.set("from", from);
    if (to) params.set("to", to);

    const donationsPromise = api.get<DonationPage>(`/donations?${params.toString()}`);
    const summaryPromise = api.get<DonationSummary>("/donations/summary");
    const fundsPromise = api.get<DonationFund[]>("/donation-funds");

    Promise.all([donationsPromise, summaryPromise, fundsPromise])
      .then(([pageData, summaryData, fundData]) => {
        setData(pageData);
        setSummary(summaryData);
        setFunds(fundData);
        setError(null);
      })
      .catch((err) => {
        setError(describeError(err));
      })
      .finally(() => {
        setLoading(false);
      });
  }, [page, from, to]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- data load on filter/page change
    loadData();
  }, [loadData]);

  // Client-side mode filter on current page items if selected
  const displayedItems = (data?.items ?? []).filter((item) => {
    if (modeFilter === "ALL") return true;
    return item.mode === modeFilter;
  });

  async function handleRecordSubmit(e: React.FormEvent) {
    e.preventDefault();
    setRecordError(null);
    if (!recordDonor.trim()) {
      setRecordError("Donor name is required.");
      return;
    }
    // Same rule as the API (exact rupees, at most 2 decimals); the server converts to paise.
    if (!/^(?:[1-9]\d{0,8}(?:\.\d{1,2})?|0\.\d{1,2})$/.test(recordAmount.trim()) || /^0\.0{1,2}$/.test(recordAmount.trim())) {
      setRecordError("Enter an amount like 1500 or 1500.50.");
      return;
    }

    setRecording(true);
    try {
      await api.request("/donations", {
        method: "POST",
        json: {
          donorName: recordDonor.trim(),
          amount: recordAmount.trim(),
          mode: recordMode,
          purpose: recordPurpose.trim() || null,
          fundId: recordFund ? Number(recordFund) : null,
          reference: recordReference.trim() || null,
          receivedOn: recordDate,
        },
      });
      setShowRecordModal(false);
      // Reset form
      setRecordDonor("");
      setRecordAmount("");
      setRecordPurpose("");
      setRecordReference("");
      loadData();
    } catch (err) {
      setRecordError(describeError(err));
    } finally {
      setRecording(false);
    }
  }

  async function handleReverseSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!reversingDonation) return;
    setReversalError(null);
    if (reversalReason.trim().length < 10) {
      setReversalError("Reason must be at least 10 characters.");
      return;
    }

    setReversing(true);
    try {
      await api.request(`/donations/${reversingDonation.id}/reverse`, {
        method: "POST",
        json: {
          reason: reversalReason.trim(),
        },
      });
      setReversingDonation(null);
      setReversalReason("");
      loadData();
    } catch (err) {
      setReversalError(describeError(err));
    } finally {
      setReversing(false);
    }
  }

  async function handleOpenReceipt(item: DonationItem) {
    setReceiptLoading(true);
    setReceiptError(null);
    setViewingReceipt(null);
    setIssueForDonation(null);

    try {
      const receipt = await api.get<ReceiptDetail>(`/donations/${item.id}/receipt`);
      setViewingReceipt(receipt);
    } catch (err) {
      // Only "no receipt yet" (404) offers to issue one; any other failure is shown as an error,
      // so a transient error never invites issuing a duplicate.
      if (err instanceof ApiError && err.status === 404) {
        setIssueForDonation(item);
        setIssuePan("");
        setIssueAddress("");
      } else {
        setReceiptError(describeError(err));
      }
    } finally {
      setReceiptLoading(false);
    }
  }

  async function handleIssueReceipt(e: React.FormEvent) {
    e.preventDefault();
    if (!issueForDonation) return;
    if (!issuePan.trim()) {
      setReceiptError("Donor PAN is required for 80G receipts.");
      return;
    }
    if (!issueAddress.trim()) {
      setReceiptError("Donor address is required for 80G receipts.");
      return;
    }

    setIssuing(true);
    setReceiptError(null);
    try {
      const receipt = await api.request<ReceiptDetail>(`/donations/${issueForDonation.id}/receipt`, {
        method: "POST",
        json: {
          donorPan: issuePan.trim(),
          donorAddress: issueAddress.trim(),
        },
      });
      setIssueForDonation(null);
      // Never carry one donor's PAN into the next receipt.
      setIssuePan("");
      setIssueAddress("");
      setViewingReceipt(receipt);
    } catch (err) {
      setReceiptError(describeError(err));
    } finally {
      setIssuing(false);
    }
  }

  function formatInr(val: string | number) {
    const num = typeof val === "string" ? parseFloat(val) : val;
    if (isNaN(num)) return "₹0.00";
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 2,
    }).format(num);
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-2 border-b border-stone-200">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-stone-900">{t.donations.title}</h1>
          <p className="mt-1 text-sm text-stone-500">{t.donations.description}</p>
        </div>
        <div className="flex items-center gap-3">
          <Link
            href="/donate"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold text-stone-700 bg-stone-100 hover:bg-stone-200 rounded-lg border border-stone-300 transition-colors shadow-sm"
          >
            <span>Public donate page</span>
            <span className="text-xs">↗</span>
          </Link>
          {canRecord && (
            <Button
              variant="primary"
              onClick={() => setShowRecordModal(true)}
              className="inline-flex items-center gap-2 shadow-sm"
            >
              <span>+</span>
              <span>{t.donations.recordDonation}</span>
            </Button>
          )}
        </div>
      </div>

      {error && <Alert tone="danger">{error}</Alert>}

      {/* Financial Summary Cards */}
      {summary && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          <Card className="p-4 bg-gradient-to-br from-amber-50 to-orange-50 border-amber-200">
            <span className="text-xs font-semibold uppercase tracking-wider text-amber-800">
              {t.donations.totalCollection} (FY {summary.financialYear})
            </span>
            <div className="mt-2 text-2xl font-extrabold text-amber-950">
              {formatInr(summary.net)}
            </div>
            <div className="mt-1 text-xs text-amber-700 font-medium">
              Net balance across all modes
            </div>
          </Card>

          {summary.byMode.map((m) => (
            <Card key={m.mode} className="p-4 bg-white border-stone-200">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-stone-600">
                  {m.mode}
                </span>
                <span className="text-xs px-2 py-0.5 rounded bg-stone-100 text-stone-600 font-medium">
                  {m.donations} entries
                </span>
              </div>
              <div className="mt-2 text-xl font-bold text-stone-900">
                {formatInr(m.net)}
              </div>
              {m.reversals > 0 && (
                <div className="mt-1 text-xs text-rose-600 font-medium">
                  {m.reversals} reversed
                </div>
              )}
            </Card>
          ))}
        </div>
      )}

      {summary && summary.byFund.length > 0 && (
        <Card className="p-4">
          <h2 className="mb-3 text-sm font-semibold">By fund (FY {summary.financialYear})</h2>
          <ul className="flex flex-col gap-1 text-sm">
            {summary.byFund.map((f) => (
              <li key={f.fundId ?? "general"} className="flex justify-between gap-3">
                <span>{f.fund}</span>
                <span className="font-medium">{formatInr(f.net)} <span className="text-muted">· {f.donations} entries</span></span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {canReverse && (
        <Card className="p-4">
          <h2 className="mb-1 text-sm font-semibold">Funds</h2>
          <p className="mb-3 text-xs text-muted">Earmarked funds donors and staff can choose. Turn one off to stop new gifts; its history stays.</p>
          <ul className="mb-3 flex flex-col gap-1 text-sm">
            {funds.map((f) => (
              <li key={f.id} className="flex items-center justify-between gap-3">
                <span className={f.active ? "" : "text-muted line-through"}>{f.name}</span>
                <Button variant="ghost" onClick={() => void saveFund(f.id, f.name, !f.active)}>{f.active ? "Turn off" : "Turn on"}</Button>
              </li>
            ))}
          </ul>
          <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); void saveFund(null, newFund, true); }}>
            <input aria-label="New fund name" value={newFund} onChange={(e) => setNewFund(e.target.value)} maxLength={80}
                   placeholder="e.g. Annadanam fund" className="flex-1 rounded-lg border border-stone-300 px-3 py-2 text-sm" />
            <Button type="submit" variant="secondary">Add fund</Button>
          </form>
          {fundError ? <div className="mt-2"><Alert tone="danger">{fundError}</Alert></div> : null}
        </Card>
      )}

      {/* Filter and Search Bar */}
      <Card className="p-4 bg-stone-50 border-stone-200">
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex items-center gap-2">
            <label className="text-xs font-semibold text-stone-600">{t.donations.filterFrom}:</label>
            <input
              type="date"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setPage(0);
              }}
              className="text-xs px-2.5 py-1.5 rounded-md border border-stone-300 bg-white text-stone-800 focus:outline-none focus:ring-2 focus:ring-amber-500"
            />
          </div>

          <div className="flex items-center gap-2">
            <label className="text-xs font-semibold text-stone-600">{t.donations.filterTo}:</label>
            <input
              type="date"
              value={to}
              onChange={(e) => {
                setTo(e.target.value);
                setPage(0);
              }}
              className="text-xs px-2.5 py-1.5 rounded-md border border-stone-300 bg-white text-stone-800 focus:outline-none focus:ring-2 focus:ring-amber-500"
            />
          </div>

          <div className="flex items-center gap-2">
            <label className="text-xs font-semibold text-stone-600">{t.donations.modeLabel}:</label>
            <select
              value={modeFilter}
              onChange={(e) => setModeFilter(e.target.value)}
              className="text-xs px-2.5 py-1.5 rounded-md border border-stone-300 bg-white text-stone-800 focus:outline-none focus:ring-2 focus:ring-amber-500"
            >
              <option value="ALL">{t.donations.allModes}</option>
              {DONATION_MODES.map((mode) => (
                <option key={mode} value={mode}>
                  {mode}
                </option>
              ))}
            </select>
          </div>

          {(from || to || modeFilter !== "ALL") && (
            <button
              onClick={() => {
                setFrom("");
                setTo("");
                setModeFilter("ALL");
                setPage(0);
              }}
              className="text-xs text-amber-700 hover:text-amber-800 font-semibold underline ml-auto"
            >
              Reset Filters
            </button>
          )}
        </div>
      </Card>

      {/* Donations Table */}
      <Card className="overflow-hidden border-stone-200">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-stone-200 text-left text-sm">
            <thead className="bg-stone-50 text-xs font-semibold text-stone-600 uppercase tracking-wider">
              <tr>
                <th className="py-3 px-4">{t.donations.colDate}</th>
                <th className="py-3 px-4">{t.donations.colDonor}</th>
                <th className="py-3 px-4">{t.donations.colAmount}</th>
                <th className="py-3 px-4">{t.donations.colMode}</th>
                <th className="py-3 px-4">{t.donations.colPurpose}</th>
                <th className="py-3 px-4">Ref / UTR</th>
                <th className="py-3 px-4">{t.donations.colSource}</th>
                <th className="py-3 px-4 text-right">{t.donations.colActions}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-200 bg-white">
              {loading && !data && (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-stone-500">
                    <Spinner />
                    <p className="mt-2 text-xs">{t.common.loading}</p>
                  </td>
                </tr>
              )}

              {!loading && displayedItems.length === 0 && (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-stone-500">
                    <div className="max-w-xs mx-auto text-center">
                      <p className="text-stone-400 text-3xl mb-2">🪙</p>
                      <p className="font-semibold text-stone-700">{t.donations.emptyState}</p>
                      <p className="text-xs text-stone-400 mt-1">
                        Use &ldquo;Record donation&rdquo; for offline gifts; online donations appear here automatically.
                      </p>
                    </div>
                  </td>
                </tr>
              )}

              {displayedItems.map((item) => {
                const isReversal = item.reversesId !== null || item.amount.startsWith("-");
                const isReversed = !isReversal && data?.items.some((other) => other.reversesId === item.id);

                return (
                  <tr
                    key={item.id}
                    className={`hover:bg-stone-50/80 transition-colors ${
                      isReversal ? "bg-rose-50/40" : isReversed ? "opacity-60 bg-stone-50/50" : ""
                    }`}
                  >
                    <td className="py-3 px-4 whitespace-nowrap text-xs font-mono text-stone-600">
                      {item.receivedOn}
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap">
                      <div className="font-medium text-stone-900">
                        {item.devoteeId ? (
                          <Link
                            href={`/devotees/${item.devoteeId}`}
                            className="text-amber-800 hover:text-amber-900 underline font-semibold"
                          >
                            {item.donorName}
                          </Link>
                        ) : (
                          item.donorName
                        )}
                      </div>
                      {isReversal && item.reversalReason && (
                        <div className="text-xs text-rose-700 italic mt-0.5">
                          ↳ Reason: {item.reversalReason}
                        </div>
                      )}
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap font-mono font-bold">
                      <span
                        className={
                          isReversal
                            ? "text-rose-600"
                            : isReversed
                            ? "text-stone-400 line-through"
                            : "text-emerald-700"
                        }
                      >
                        {formatInr(item.amount)}
                      </span>
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap">
                      <span
                        className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${
                          item.mode === "UPI"
                            ? "bg-purple-100 text-purple-800"
                            : item.mode === "CASH"
                            ? "bg-emerald-100 text-emerald-800"
                            : item.mode === "CARD"
                            ? "bg-blue-100 text-blue-800"
                            : "bg-stone-100 text-stone-800"
                        }`}
                      >
                        {item.mode}
                        {item.channel === "ONLINE" ? (
                          <span className="ml-1 rounded-full bg-emerald-50 px-1.5 py-0.5 text-[10px] font-semibold text-emerald-700"
                                title={item.paymentRef ? `Razorpay ${item.paymentRef}` : "Paid online"}>
                            Online
                          </span>
                        ) : null}
                      </span>
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap text-stone-600 text-xs">
                      {item.purpose || <span className="text-stone-300">—</span>}
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap text-stone-500 font-mono text-xs">
                      {item.reference || <span className="text-stone-300">—</span>}
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap">
                      {item.recordedBy === null ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
                          <span>🌐</span>
                          <span>{t.donations.sourceOnline}</span>
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200">
                          <span>🏛️</span>
                          <span>{t.donations.sourceCounter}</span>
                        </span>
                      )}
                    </td>

                    <td className="py-3 px-4 whitespace-nowrap text-right text-xs">
                      <div className="flex items-center justify-end gap-2">
                        {!isReversal && (
                          <button
                            onClick={() => handleOpenReceipt(item)}
                            className="px-2.5 py-1 rounded bg-stone-100 hover:bg-stone-200 text-stone-700 font-medium transition-colors border border-stone-300"
                          >
                            {t.donations.receiptButton}
                          </button>
                        )}

                        {canReverse && !isReversal && !isReversed && (
                          <button
                            onClick={() => {
                              setReversingDonation(item);
                              setReversalReason("");
                              setReversalError(null);
                            }}
                            className="px-2 py-1 rounded hover:bg-rose-50 text-rose-600 hover:text-rose-700 font-medium transition-colors"
                          >
                            {t.donations.reverseButton}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Pagination Bar */}
        {data && data.total > 0 && (
          <div className="p-4 bg-stone-50 border-t border-stone-200 flex items-center justify-between">
            <span className="text-xs text-stone-500 font-medium">
              {t.donations.pageShowing(
                data.page * data.size + 1,
                Math.min((data.page + 1) * data.size, data.total),
                data.total
              )}
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="text-xs px-2.5 py-1.5"
              >
                Previous
              </Button>
              <Button
                variant="secondary"
                disabled={(page + 1) * PAGE_SIZE >= data.total}
                onClick={() => setPage((p) => p + 1)}
                className="text-xs px-2.5 py-1.5"
              >
                Next
              </Button>
            </div>
          </div>
        )}
      </Card>

      {/* Record Counter Donation Modal */}
      {showRecordModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in">
          <Card className="max-w-lg w-full p-6 bg-white shadow-2xl relative">
            <div className="flex items-center justify-between pb-3 border-b border-stone-200 mb-4">
              <h2 className="text-lg font-bold text-stone-900">{t.donations.recordModalTitle}</h2>
              <button
                onClick={() => setShowRecordModal(false)}
                className="text-stone-400 hover:text-stone-600 text-lg font-bold"
              >
                ✕
              </button>
            </div>

            {recordError && (
              <div className="mb-4">
                <Alert tone="danger">{recordError}</Alert>
              </div>
            )}

            <form onSubmit={handleRecordSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  {t.donations.donorNameLabel} <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={recordDonor}
                  onChange={(e) => setRecordDonor(e.target.value)}
                  placeholder="e.g. Ramesh Chandra Sharma"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-stone-300 focus:outline-none focus:ring-2 focus:ring-amber-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    {t.donations.amountLabel} <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="number"
                    step="1"
                    min="1"
                    required
                    value={recordAmount}
                    onChange={(e) => setRecordAmount(e.target.value)}
                    placeholder="1000"
                    className="w-full px-3 py-2 text-sm rounded-lg border border-stone-300 focus:outline-none focus:ring-2 focus:ring-amber-500 font-mono font-bold"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    {t.donations.modeLabel}
                  </label>
                  <select
                    value={recordMode}
                    onChange={(e) => setRecordMode(e.target.value as DonationMode)}
                    className="w-full px-3 py-2 text-sm rounded-lg border border-stone-300 focus:outline-none focus:ring-2 focus:ring-amber-500 bg-white"
                  >
                    {DONATION_MODES.map((mode) => (
                      <option key={mode} value={mode}>
                        {mode}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {funds.some((f) => f.active) ? (
                <div>
                  <label htmlFor="record-fund" className="block text-xs font-semibold text-stone-700 mb-1">Fund</label>
                  <select id="record-fund" value={recordFund} onChange={(e) => setRecordFund(e.target.value)}
                          className="w-full px-3 py-2 text-sm rounded-lg border border-stone-300 bg-white">
                    <option value="">General fund</option>
                    {funds.filter((f) => f.active).map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
                  </select>
                </div>
              ) : null}

              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  {t.donations.purposeLabel}
                </label>
                <input
                  type="text"
                  value={recordPurpose}
                  onChange={(e) => setRecordPurpose(e.target.value)}
                  placeholder="e.g. Annadanam, Mandir Nirman, Deepotsav"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-stone-300 focus:outline-none focus:ring-2 focus:ring-amber-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    {t.donations.referenceLabel}
                  </label>
                  <input
                    type="text"
                    value={recordReference}
                    onChange={(e) => setRecordReference(e.target.value)}
                    placeholder="UTR / Cheque No"
                    className="w-full px-3 py-2 text-sm rounded-lg border border-stone-300 focus:outline-none focus:ring-2 focus:ring-amber-500 font-mono"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-stone-700 mb-1">
                    {t.donations.dateLabel}
                  </label>
                  <input
                    type="date"
                    required
                    value={recordDate}
                    onChange={(e) => setRecordDate(e.target.value)}
                    className="w-full px-3 py-2 text-sm rounded-lg border border-stone-300 focus:outline-none focus:ring-2 focus:ring-amber-500"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-stone-200">
                <Button variant="secondary" type="button" onClick={() => setShowRecordModal(false)}>
                  {t.common.cancel}
                </Button>
                <Button variant="primary" type="submit" disabled={recording}>
                  {recording ? t.donations.recording : t.donations.submitRecord}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* Reversal Confirmation Modal (TRUST_ADMIN only) */}
      {reversingDonation && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in">
          <Card className="max-w-md w-full p-6 bg-white shadow-2xl relative">
            <div className="flex items-center justify-between pb-3 border-b border-stone-200 mb-4">
              <h2 className="text-lg font-bold text-rose-800">{t.donations.reversalModalTitle}</h2>
              <button
                onClick={() => setReversingDonation(null)}
                className="text-stone-400 hover:text-stone-600 text-lg font-bold"
              >
                ✕
              </button>
            </div>

            <div className="bg-rose-50 border border-rose-200 rounded-lg p-3 text-xs text-rose-900 mb-4">
              <p className="font-bold">Permanent Ledger Action:</p>
              <p className="mt-1">
                Reversing will add a matching negative entry to the ledger and immediately cancel any issued 80G receipts.
              </p>
              <div className="mt-2 pt-2 border-t border-rose-200 font-mono">
                Donor: {reversingDonation.donorName} | Amount: {formatInr(reversingDonation.amount)}
              </div>
            </div>

            {reversalError && (
              <div className="mb-4">
                <Alert tone="danger">{reversalError}</Alert>
              </div>
            )}

            <form onSubmit={handleReverseSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  {t.donations.reversalReasonLabel} <span className="text-rose-500">*</span>
                </label>
                <textarea
                  required
                  rows={3}
                  value={reversalReason}
                  onChange={(e) => setReversalReason(e.target.value)}
                  placeholder="e.g. Duplicate entry by counter staff; verified with bank statement"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-stone-300 focus:outline-none focus:ring-2 focus:ring-rose-500"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-stone-200">
                <Button variant="secondary" type="button" onClick={() => setReversingDonation(null)}>
                  {t.common.cancel}
                </Button>
                <Button variant="danger" type="submit" disabled={reversing}>
                  {reversing ? t.donations.reversing : t.donations.submitReversal}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* Official Receipt Viewer Modal */}
      {viewingReceipt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in print:p-0 print:bg-white">
          <Card className="max-w-xl w-full p-8 bg-white shadow-2xl relative border-amber-200 print:shadow-none print:border-none">
            <button
              onClick={() => setViewingReceipt(null)}
              className="absolute top-4 right-4 text-stone-400 hover:text-stone-600 text-xl font-bold print:hidden"
            >
              ✕
            </button>

            {/* Receipt Watermark / Header */}
            <div className="text-center pb-6 border-b-2 border-amber-800">
              <span className="text-amber-800 font-extrabold text-2xl tracking-wide uppercase block">
                {viewingReceipt.trustLegalName}
              </span>
              <p className="text-xs text-stone-600 mt-1 max-w-md mx-auto">{viewingReceipt.trustAddress}</p>
              <div className="flex items-center justify-center gap-4 mt-2 text-xs font-mono text-stone-700">
                <span>PAN: <strong>{viewingReceipt.trustPan}</strong></span>
                <span>80G Reg: <strong>{viewingReceipt.trustRegistration80g}</strong></span>
              </div>
              <div className="mt-3 inline-block px-3 py-1 bg-amber-100 text-amber-900 rounded font-bold text-xs uppercase tracking-wider">
                Official Donation Receipt (Under Sec 80G)
              </div>
            </div>

            {/* Receipt Details */}
            <div className="mt-6 space-y-4 text-sm text-stone-800">
              <div className="flex justify-between items-center bg-stone-50 p-3 rounded border border-stone-200">
                <div>
                  <span className="text-xs text-stone-500 uppercase block font-semibold">Receipt Number</span>
                  <span className="font-mono font-bold text-base text-amber-900">{viewingReceipt.number}</span>
                </div>
                <div className="text-right">
                  <span className="text-xs text-stone-500 uppercase block font-semibold">Date of Receipt</span>
                  <span className="font-mono font-bold">{viewingReceipt.issuedOn}</span>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 pt-2">
                <div>
                  <span className="text-xs text-stone-500 block">Received with thanks from:</span>
                  <span className="font-bold text-stone-900 text-base">{viewingReceipt.donorName}</span>
                  <p className="text-xs text-stone-600 mt-0.5">{viewingReceipt.donorAddress}</p>
                </div>
                <div className="text-right">
                  <span className="text-xs text-stone-500 block">Donor PAN:</span>
                  <span className="font-mono font-bold text-stone-900">{viewingReceipt.donorPan}</span>
                </div>
              </div>

              <div className="border-t border-stone-200 pt-3 flex justify-between items-center">
                <div>
                  <span className="text-xs text-stone-500 block">Amount in Rupees:</span>
                  <span className="font-mono font-extrabold text-2xl text-emerald-800">
                    {formatInr(viewingReceipt.amount)}
                  </span>
                </div>
                <div className="text-right">
                  <span className="text-xs text-stone-500 block">Payment Mode:</span>
                  <span className="font-bold text-stone-900 uppercase">{viewingReceipt.mode}</span>
                </div>
              </div>

              {viewingReceipt.cancelled && (
                <div className="p-3 bg-rose-50 border border-rose-300 rounded text-rose-800 text-xs font-semibold">
                  ⚠️ CANCELLED: {viewingReceipt.cancellationReason}
                </div>
              )}

              <p className="text-xs text-stone-500 italic pt-2">
                Donations to this trust are eligible for tax deduction under Section 80G of the Income Tax Act, 1961.
              </p>
            </div>

            {/* Actions */}
            <div className="mt-8 flex justify-end gap-3 print:hidden border-t border-stone-200 pt-4">
              <Button variant="secondary" onClick={() => setViewingReceipt(null)}>
                {t.common.close}
              </Button>
              <Button variant="primary" onClick={() => window.print()}>
                🖨️ Print Official Receipt
              </Button>
            </div>
          </Card>
        </div>
      )}

      {/* Issue 80G Receipt Modal (when no receipt exists yet) */}
      {issueForDonation && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in">
          <Card className="max-w-md w-full p-6 bg-white shadow-2xl relative">
            <div className="flex items-center justify-between pb-3 border-b border-stone-200 mb-4">
              <h2 className="text-lg font-bold text-amber-900">Issue 80G Tax Receipt</h2>
              <button
                onClick={() => setIssueForDonation(null)}
                className="text-stone-400 hover:text-stone-600 text-lg font-bold"
              >
                ✕
              </button>
            </div>

            <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-xs text-amber-900 mb-4">
              <p className="font-semibold">Donation Details:</p>
              <div className="mt-1 font-mono">
                Donor: {issueForDonation.donorName} | Amount: {formatInr(issueForDonation.amount)} ({issueForDonation.mode})
              </div>
            </div>

            {receiptError && (
              <div className="mb-4">
                <Alert tone="danger">{receiptError}</Alert>
              </div>
            )}

            <form onSubmit={handleIssueReceipt} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Donor PAN Number <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  maxLength={10}
                  autoComplete="off"
                  spellCheck={false}
                  value={issuePan}
                  onChange={(e) => setIssuePan(e.target.value.toUpperCase())}
                  placeholder="e.g. ABCPE1234F"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-stone-300 focus:outline-none focus:ring-2 focus:ring-amber-500 font-mono uppercase"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 mb-1">
                  Donor Postal Address <span className="text-rose-500">*</span>
                </label>
                <textarea
                  required
                  rows={3}
                  value={issueAddress}
                  onChange={(e) => setIssueAddress(e.target.value)}
                  placeholder="Full residential or office address for tax receipt"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-stone-300 focus:outline-none focus:ring-2 focus:ring-amber-500"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-stone-200">
                <Button variant="secondary" type="button" onClick={() => setIssueForDonation(null)}>
                  {t.common.cancel}
                </Button>
                <Button variant="primary" type="submit" disabled={issuing}>
                  {issuing ? "Generating Receipt…" : "Generate 80G Receipt"}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}
    </div>
  );
}
