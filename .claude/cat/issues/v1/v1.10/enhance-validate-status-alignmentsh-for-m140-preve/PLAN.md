# Plan: enhance-validate-status-alignmentsh-for-m140-preve

## Objective
enhance validate-status-alignment.sh for M140 prevention

## Details
Improved nested box validation to catch missing outer borders:
- Inner content lines must end with │...│ (inner + outer)
- Inner top borders must end with ╮...│
- Inner bottom borders must end with ╯...│

This catches the exact error pattern observed in M140 where inner
box lines were missing their outer right border.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
