"use client";
import { useApprovals } from "@/lib/api";
import { ApprovalCard } from "@/components/ApprovalCard";
import { Card } from "@/components/Card";
import { Skeleton } from "@/components/Skeleton";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/Tabs";
import { useState } from "react";
import type { Change } from "@/lib/types";
import { useUi } from "@/lib/store";
import { ROLE_LABELS } from "@/lib/constants";

export default function ApprovalsPage() {
  const { data, isLoading, error, mutate } = useApprovals();
  const [tab, setTab] = useState<"queue" | "draft" | "completed">("queue");
  const user = useUi((s) => s.user);

  function filterFn(c: Change) {
    if (tab === "queue") return c.status === "PENDING_APPROVAL";
    if (tab === "draft") return c.status === "DRAFT";
    return c.status === "APPROVED" || c.status === "REJECTED" || c.status === "EXPIRED";
  }

  const visible = (data ?? []).filter(filterFn);
  const canAny = user.roles.length > 0;

  return (
    <div className="space-y-4">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Approvals</h1>
          <p className="text-sm text-muted-foreground">
            You can sign as: {user.roles.length ? user.roles.map((r) => ROLE_LABELS[r].title).join(", ") : <em>no roles</em>}.
            Self-approval is blocked (EMS-CFG-1201).
          </p>
        </div>
      </header>

      <Tabs value={tab} onValueChange={(v) => setTab(v as any)}>
        <TabsList>
          <TabsTrigger value="queue">Queue</TabsTrigger>
          <TabsTrigger value="draft">Drafts</TabsTrigger>
          <TabsTrigger value="completed">Completed</TabsTrigger>
        </TabsList>

        <TabsContent value={tab}>
          {isLoading && <Skeleton rows={4} />}
          {error && <Card><div className="text-sm text-destructive">Failed to load: {String(error)}</div></Card>}
          {!isLoading && visible.length === 0 && (
            <Card><div className="text-sm text-muted-foreground">Nothing in this view.</div></Card>
          )}
          <div className="space-y-4">
            {visible.map((c) => (
              <ApprovalCard key={c.id} change={c} onChanged={() => mutate()} />
            ))}
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}