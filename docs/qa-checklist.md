# ClinicBook — Frontend QA Checklist (Phase 5)

Manual exploratory checklist for the React frontend. Backend-only Phase 5 work
(JaCoCo, endpoint authorization, request-id logging, Actuator, query tuning) is
covered by the backend checklist; this document verifies the user-facing
behaviour that depends on it.

## Environment

| Item          | Value                                                    |
| ------------- | -------------------------------------------------------- |
| Branch        | `chore/p5-quality`                                       |
| App URL       | http://localhost:5173 (or the preview build)             |
| API           | Spring Boot at http://localhost:8080, or MSW mock mode   |
| Browsers      | Chromium (primary), Firefox, Safari/WebKit if available  |
| Viewports     | 360×640 (mobile), 768×1024 (tablet), 1280×800 (desktop)  |
| Screen reader | NVDA + Firefox or VoiceOver + Safari (at least one pass) |
| Date/time     | Note the clinic time zone; appointments display UTC      |

Run with mocks:

```sh
cd frontend
npm install
cp .env.example .env      # set VITE_API_MOCKING=enabled
npm run dev
```

Run against the real backend:

```sh
cd backend && ./mvnw spring-boot:run
cd frontend && npm run dev
```

Test accounts (mock mode accepts any password; see `frontend/README.md`):

| Email                 | Role    |
| --------------------- | ------- |
| `patient@example.com` | PATIENT |
| `doctor@example.com`  | DOCTOR  |
| `admin@example.com`   | ADMIN   |

Record each item as **Pass**, **Fail**, **Blocked**, or **N/A**, with the defect
ID when it fails. Severity: **S1** data loss/security, **S2** core flow broken,
**S3** degraded but usable, **S4** cosmetic.

## A. Smoke and navigation

- [ ] A1. App loads without console errors at `/`.
- [ ] A2. Header shows brand, primary links, and the correct role badge after login.
- [ ] A3. Links navigate without a full page reload; back/forward work.
- [ ] A4. Browser tab title updates per route (home, doctors, admin, 404).
- [ ] A5. Footer and main content are visible at every viewport.
- [ ] A6. Refreshing a deep link (`/doctors/1`, `/admin`, `/appointments`) restores the page.

## B. Authentication and session

- [ ] B1. `/login` validates email/password client-side and focuses nothing unexpectedly.
- [ ] B2. Invalid credentials show one readable alert; field values are preserved.
- [ ] B3. Rate-limited login (429) shows the "wait a moment" warning.
- [ ] B4. `/register` validates name, email, password and optional phone.
- [ ] B5. Successful registration logs the patient in.
- [ ] B6. Logout clears the session and lands on `/login`; a protected URL then redirects to login.
- [ ] B7. A disabled account cannot log in (`ACCOUNT_DISABLED`) and sees a clear message.
- [ ] B8. Session refresh does not flash the logged-out header.

## C. Doctor search and profile

- [ ] C1. Doctor list loads with names, specialty, fee and rating.
- [ ] C2. Text search, specialty filter, sort and reset update results.
- [ ] C3. Pagination works and announces the visible range.
- [ ] C4. Empty search shows the empty state, not a blank area.
- [ ] C5. Doctor profile shows bio, contact details, fee and average rating.
- [ ] C6. Review list loads, paginates and shows "No reviews yet" when empty.

## D. Booking flow

- [ ] D1. Date strip scrolls horizontally on mobile; the selected date is visibly distinct.
- [ ] D2. Slots load for the selected date; past slots are disabled and labelled.
- [ ] D3. Selecting a slot reveals the confirmation summary (doctor, date, time, fee).
- [ ] D4. Confirming creates a `PENDING` appointment and shows the success notice.
- [ ] D5. "View my appointments" opens the appointment list with the new booking.
- [ ] D6. Booking the same slot twice returns `SLOT_TAKEN`; the list refreshes and the message explains the race.
- [ ] D7. A fourth active booking is rejected with the booking-limit message.
- [ ] D8. Anonymous visitors see a login prompt and cannot book.
- [ ] D9. Doctor/admin accounts see "Only patients can book" and disabled slots.
- [ ] D10. An API failure shows the retryable error; Retry recovers after the network returns.

## E. Appointment lifecycle

- [ ] E1. Patient sees upcoming and past appointments with status badges.
- [ ] E2. Cancel works outside the 2-hour cutoff; the badge changes to `CANCELLED`.
- [ ] E3. Inside the cutoff the cancel button is disabled with an explanation; a forced 409 shows the friendly message.
- [ ] E4. Doctor can confirm a `PENDING` booking.
- [ ] E5. Doctor can complete a `CONFIRMED` booking or mark it `NO_SHOW`.
- [ ] E6. Terminal appointments show no lifecycle actions.
- [ ] E7. Admin sees the "no personal appointment list" notice.
- [ ] E8. Pagination and busy/disabled states behave while actions are in flight.

## F. Reviews

- [ ] F1. Only `COMPLETED` patient appointments offer "Leave a review".
- [ ] F2. Rating is required; submitting without stars shows a validation message.
- [ ] F3. A review with a comment saves and shows "Review submitted".
- [ ] F4. Reviewing the same appointment again (409) shows "already reviewed".
- [ ] F5. Doctor rating and review list update after submission.
- [ ] F6. Comment length is capped at 1000 characters with a visible counter.

## G. Admin dashboard

- [ ] G1. Admin-only route; patient/doctor are redirected away.
- [ ] G2. Default range shows the last 30 days.
- [ ] G3. Changing from/to/top-doctors and applying refreshes the data.
- [ ] G4. `from > to` is blocked client-side; a forced `INVALID_DATE_RANGE` is readable.
- [ ] G5. A range over 366 days shows the `DATE_RANGE_TOO_LARGE` message.
- [ ] G6. Summary cards and both charts match the returned numbers.
- [ ] G7. Top-doctors table shows bookings, completed visits and rating.
- [ ] G8. Empty periods show empty states rather than broken charts.

## H. Admin user management

- [ ] H1. Admin-only route; search and role filter work together.
- [ ] H2. Results show name, email, role, status and join date; count is announced.
- [ ] H3. Pagination works and disables at the ends.
- [ ] H4. Disabling a patient then enabling them restores access.
- [ ] H5. Changing a patient to admin and back works.
- [ ] H6. Your own row is marked "You"; role and status controls are disabled.
- [ ] H7. Doctor role controls are disabled with an explanation.
- [ ] H8. Last enabled admin cannot be disabled/demoted (UI or 409 message).
- [ ] H9. A forced `DOCTOR_ROLE_MANAGED_SEPARATELY` shows the mapped message.
- [ ] H10. A user removed by another session shows `USER_NOT_FOUND` and the list can refresh.

## I. Notification preferences

- [ ] I1. Toggles reflect defaults and persist after Save + reload.
- [ ] I2. "Reset to defaults" clears the stored override.
- [ ] I3. The preview/backend limitation notice is visible and unambiguous.
- [ ] I4. Toggles are keyboard-operable and announce on/off state.

## J. Loading, empty and error states

- [ ] J1. Every data screen shows a labelled loading indicator.
- [ ] J2. Slow 3G throttling keeps layout stable and does not double-submit.
- [ ] J3. Offline/500 responses show a retryable error, not a blank screen.
- [ ] J4. Empty lists show a helpful next step (search reset, book a doctor, etc.).
- [ ] J5. A render-time crash shows the error boundary with "Try again" and "Go home".
- [ ] J6. Unknown routes show the 404 page with working Home/Browse links.

## K. Accessibility

- [ ] K1. Tab order is logical; every interactive control is reachable.
- [ ] K2. A visible skip link appears on first Tab and jumps to `#main-content`.
- [ ] K3. Focus ring is visible on links, buttons, inputs, selects and toggles.
- [ ] K4. After client-side navigation, focus moves to main content and the title updates.
- [ ] K5. All form controls have programmatic labels; errors use `aria-invalid` and are announced.
- [ ] K6. Status badges, ratings and star buttons expose text alternatives.
- [ ] K7. Charts expose their data as an accessible table/list.
- [ ] K8. Admin tables have captions and column headers.
- [ ] K9. Colour contrast meets WCAG AA for body text, metadata and status pills.
- [ ] K10. `prefers-reduced-motion` stops the spinner/transition animations.
- [ ] K11. 200% zoom and 320 px width keep content usable without horizontal page scroll.
- [ ] K12. No keyboard trap in the date strip, slot grid, dialogs or menus.

## L. Responsive layout

- [ ] L1. Header wraps cleanly at 360 px; no control is clipped.
- [ ] L2. Date strip and slot grid scroll/reflow without overlap.
- [ ] L3. Appointment cards stack actions below content on mobile.
- [ ] L4. Admin tables scroll horizontally inside their card, not the whole page.
- [ ] L5. Charts remain readable at 360 px and 1280 px.
- [ ] L6. Long names, emails and comments wrap instead of overflowing.

## M. 404 and recovery

- [ ] M1. `/this-does-not-exist` shows the 404 page inside the normal layout.
- [ ] M2. Title reads "Page not found".
- [ ] M3. Home and Browse doctors links work.
- [ ] M4. Back button returns to the previous valid page.

## Defect log

| ID  | Area | Steps to reproduce | Expected | Actual | Severity | Status |
| --- | ---- | ------------------ | -------- | ------ | -------- | ------ |
|     |      |                    |          |        |          |        |

## Sign-off

| Role              | Name | Date | Result |
| ----------------- | ---- | ---- | ------ |
| Frontend reviewer |      |      |        |
| Backend reviewer  |      |      |        |
| Cross-review      |      |      |        |

## Known limitations

- Notification preferences are a browser-only preview; the backend does not store
  per-user preferences yet.
- Mock mode approximates backend rules (review uniqueness, admin safeguards,
  stats) but is not a substitute for a backend integration pass.
- Times are displayed in UTC throughout the frontend.
