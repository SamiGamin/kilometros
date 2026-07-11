---
name: Luminous Utility
colors:
  surface: '#0b1326'
  surface-dim: '#0b1326'
  surface-bright: '#31394d'
  surface-container-lowest: '#060e20'
  surface-container-low: '#131b2e'
  surface-container: '#171f33'
  surface-container-high: '#222a3d'
  surface-container-highest: '#2d3449'
  on-surface: '#dae2fd'
  on-surface-variant: '#c3c6d7'
  inverse-surface: '#dae2fd'
  inverse-on-surface: '#283044'
  outline: '#8d90a0'
  outline-variant: '#434655'
  surface-tint: '#b4c5ff'
  primary: '#b4c5ff'
  on-primary: '#002a78'
  primary-container: '#2563eb'
  on-primary-container: '#eeefff'
  inverse-primary: '#0053db'
  secondary: '#4edea3'
  on-secondary: '#003824'
  secondary-container: '#00a572'
  on-secondary-container: '#00311f'
  tertiary: '#ffb95f'
  on-tertiary: '#472a00'
  tertiary-container: '#996100'
  on-tertiary-container: '#ffeedd'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#dbe1ff'
  primary-fixed-dim: '#b4c5ff'
  on-primary-fixed: '#00174b'
  on-primary-fixed-variant: '#003ea8'
  secondary-fixed: '#6ffbbe'
  secondary-fixed-dim: '#4edea3'
  on-secondary-fixed: '#002113'
  on-secondary-fixed-variant: '#005236'
  tertiary-fixed: '#ffddb8'
  tertiary-fixed-dim: '#ffb95f'
  on-tertiary-fixed: '#2a1700'
  on-tertiary-fixed-variant: '#653e00'
  background: '#0b1326'
  on-background: '#dae2fd'
  surface-variant: '#242B3D'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.05em
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 10px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 16px
  margin-mobile: 20px
---

## Brand & Style
The design system is engineered for the gig economy—specifically for drivers and high-utilization users who operate in diverse lighting conditions. The brand personality is **utilitarian, professional, and empowering**. It balances the seriousness of financial management with a high-energy, modern interface.

The visual style is a blend of **Corporate Modern and Glassmorphism**. It utilizes a deep, obsidian base to reduce eye strain, while employing vibrant neon accents and subtle tonal layering to establish hierarchy. Surfaces use soft, translucent qualities to feel lightweight and responsive, ensuring that even data-dense screens feel approachable and clear.

## Colors
The palette is optimized for high-contrast visibility within a dark environment. 

- **Primary (Electric Blue):** Used for primary actions, progress indicators, and focus states. It represents stability and technology.
- **Secondary (Vibrant Green):** Reserved for "success" states, earnings, and positive growth metrics.
- **Tertiary (Warning Amber):** Used for alerts, maintenance reminders, and cautionary status.
- **Neutrals:** The background is a custom "Deep Obsidian" to prevent pure-black OLED smearing while maintaining infinite depth. Surface colors scale in lightness to indicate elevation.

## Typography
The system uses **Plus Jakarta Sans** as the primary typeface for its friendly yet geometric and modern qualities, which ensures legibility on mobile screens. For data-heavy contexts, such as transaction IDs, vehicle plates, or timestamps, **JetBrains Mono** is utilized to provide a technical, "instrument-cluster" feel.

Headlines should always use a tighter letter-spacing to feel impactful. Body text maintains a generous line height to ensure readability while the user is on the move.

## Layout & Spacing
This design system follows a **4px baseline grid** with a fluid layout model optimized for mobile-first interaction. 

- **Margins:** Standard mobile screens utilize a 20px side margin to keep content away from the bezel edge.
- **Gutter:** A 16px gutter is used between cards and list items to maintain clear separation without wasting vertical space.
- **Vertical Rhythm:** Information is grouped in logical clusters using 8px (related items) and 24px (separate sections) spacing.
- **Safe Areas:** Bottom navigation and primary action buttons must account for device home-indicators by using a minimum 34px bottom padding.

## Elevation & Depth
In this system, depth is communicated through **Tonal Layering** and **Subtle Outlines** rather than heavy shadows.

- **Level 0 (Background):** The base canvas.
- **Level 1 (Cards/Containers):** A slightly lighter blue-grey with a 1px low-opacity border (#FFFFFF10).
- **Level 2 (Modals/Popovers):** Higher luminosity surfaces with a subtle backdrop blur (20px) to maintain context of the layer below.
- **Interactive States:** When a component is pressed, it should visually "sink" by reducing its scale to 0.98x, creating a tactile feedback loop.

## Shapes
The shape language is consistently **Rounded**. This softens the high-tech, dark aesthetic to make the app feel more accessible and user-friendly.

- **Small Components (Chips/Inputs):** 0.5rem (8px) radius.
- **Standard Containers (Cards):** 1rem (16px) radius.
- **Large Sections (Bottom Sheets):** 1.5rem (24px) top-only radius.
- **Selection Indicators:** Use a pill-shape (full radius) for toggle switches and status badges to differentiate them from actionable cards.

## Components

- **Buttons:** Primary buttons are high-contrast (Blue or Green) with centered bold text. Secondary buttons use an "Outlined" style with a 1.5px border.
- **Inputs:** Text fields use the `surface-variant` color with a subtle 1px border. Floating labels are preferred to save vertical space.
- **Cards:** Used as the primary unit of organization. Each card should have a 16px internal padding. Data summaries inside cards should use `label-md` (JetBrains Mono) for numerical values.
- **Chips/Badges:** Small, rounded containers used for status (e.g., "Active", "Pending"). Use low-opacity background fills with high-opacity text of the same hue.
- **Progress Bars:** Thin 4px tracks. The active fill should use a horizontal gradient (Primary Blue to Secondary Green) to represent progress toward a goal.
- **Selection Grids:** For selecting categories (like vehicle types), use large tiles with centered icons and labels, using a 2px Primary Blue border for the selected state.