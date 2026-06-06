---
type: concept
status: draft
tags: [concept/glossary, glossary/regulatory]
---

# MAR / STOR

**MAR** ("Market Abuse Regulation") is the EU regulation criminalising **market abuse** — insider dealing, unlawful disclosure of inside information, and market manipulation (spoofing, layering, wash trades, marking the close, momentum ignition, ramping). UK MAR is the post-Brexit UK equivalent.

**STOR** ("Suspicious Transaction and Order Report") is the mandatory **filing investment firms make to regulators** when they detect possible market abuse. Firms must have systems and controls — i.e. **surveillance** — to detect suspicious patterns and file STORs within reasonable timeframes (typically same-day or next-day for clear cases).

For an EMS, MAR / STOR drives the [[arch-surveillance]] component. The component runs pattern-detection algorithms across the order/execution stream to flag spoofing (cancel-after-fill patterns), layering (deep-book churn ahead of price moves), wash trades (self-crossing), marking the close (concentrated end-of-day flow that moves prices), and front-running. Confirmed alerts go to compliance for STOR filing.

## Example

[[arch-surveillance]] flags a sequence: trader X places 50K buy at offer +1, immediately cancels, then sells 50K at offer the next millisecond — a classic spoofing pattern. The alert escalates; compliance reviews; a STOR is filed with FCA within hours describing the pattern and the trader's role.

## Why it matters in an EMS

- [[arch-surveillance]] is built around MAR pattern detection.
- STOR filing workflow integrates with [[arch-notification-service]].
- See [[arch-jurisdictional-compliance]] for MAR's place in the EU stack.

## Related

- [[arch-surveillance]] · [[arch-compliance]] · [[arch-notification-service]]
- [[rts-22-27-28]] · [[emir-sftr-csdr]] (sibling EU regimes)
- [[arch-jurisdictional-compliance]]
