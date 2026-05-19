# Medicine Info And Add Flow Plan

## Current implementation target
- Photo-first add screen with tap-to-select image support.
- On-device OCR for medicine packs and prescriptions.
- Editable medicine title so OCR results can be corrected by the user.
- Reminder alarm created from the selected time and saved medicine title.
- Medicine info preview shown after the user types or scans a likely medicine name.

## Trusted data sources for medicine identification and detail display
Use a provider abstraction so the app never depends on random web scraping.

Recommended trusted sources:
- NIH MedlinePlus Drug Information
- DailyMed label data from the U.S. National Library of Medicine
- FDA label references and package inserts
- NHS Medicines A-Z
- RxNorm for normalization and synonym matching
- OpenFDA label data when a structured API is needed

## Manual implementation notes
- Keep medicine details behind a repository interface.
- Normalize OCR text before lookup.
- Show only general information such as common uses, common side effects, warnings, and storage guidance.
- Always show the disclaimer that the app does not provide medical advice.
- If a future online provider is added, configure API keys or endpoints outside the repository and keep a local fallback.

## Next backlog items
- Improve OCR parsing for frequency and dosage hints.
- Add camera capture, not just gallery selection.
- Store the captured URI and preview thumbnail in the medicine library.
- Add reminder rescheduling after reboot.
- Add pause/unpause controls based on the free-tier limit.