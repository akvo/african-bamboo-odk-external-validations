## Description

To improve plot boundary data quality, several additional validation checks should be implemented. These checks should not reject plots, but instead generate warning flags to support Helen's data quality assessment in the DCU app.

These rules were discussed and confirmed with African Bamboo in January 2026.

When a rule is triggered, the plot should still be accepted but a warning flag should be attached to the plot so it can be reviewed more easily.

## Validation Checks

- Average GPS accuracy > 15m
- Gap between consecutive polygon points > 50m
- Uneven point spacing (Coefficient of Variation of point spacing > 0.5)
- Plot area > 20 ha
- Polygon contains only 6–10 vertices (boundary may be too rough)

> GPS accuracy should be calculated using the accuracy values recorded for the GPS points captured during boundary collection (if available).

## Expected Behaviour

Plots that trigger these rules should receive warning flags, but should not be rejected or blocked. Multiple warnings can apply to the same plot.

## Acceptance Criteria

- [ ] The checks run automatically when a plot is processed.
- [ ] Plots triggering a rule receive a warning flag.
- [ ] Plots are not rejected or blocked due to these warnings.
- [ ] Warnings are visible in the DCU app review interface.
- [ ] Multiple warnings can be attached to a single plot.