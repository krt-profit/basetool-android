# feature:*

One module per functional area, sliced vertically (UI + ViewModel + repository binding +
tests + DE/EN strings ship together). Planned modules, mirroring the feature map in
`docs/ANDROID_APP_PLAN.md` §5:

`dashboard` · `missions` · `operations` · `notifications` · `hangar` · `inventory`
(Lager) · `personal` (Mein Inventar + Blueprints) · `orders` (Aufträge) · `exchange`
(Materialbörse) · `refinery` · `bank` · `promotion` · `settings` (incl. Impressum,
Datenschutz, licenses) · `onboarding` (login, approval-pending, terms).

Admin surfaces are deliberately absent (decision Q7: web-only).
