# Spatial Utility

Spatial Utility is a growing collection of small spatial utility tools for **PICO OS 6**.

The goal of this repository is to explore new ways to **visualize, present, and interact with common file types** in a way that feels more spatial, intuitive, and native to immersive computing.

Today, the repository contains one application:

- **Spatial Chart** — a spatial spreadsheet visualizer for CSV and Excel data.

More tools are planned. The long-term intention is for this repository to become a toolbox of focused spatial utilities rather than a single app.

## Vision

Traditional desktop file tools are designed around flat windows, menus, and lists. This project asks a different question:

> What if everyday files could be explored as spatial objects instead of only as 2D documents?

Each tool in this repository is intended to be:

- **small and focused**
- **useful on its own**
- **designed for spatial interaction first**
- **built specifically with PICO OS 6 in mind**

## Current App: Spatial Chart

**Spatial Chart** turns spreadsheet-like data into a 3D chart inside a spatial scene.

It is designed for quickly loading tabular data and exploring it as a volumetric visualization instead of a flat chart.

### Supported file formats

Currently supported:

- `.csv`
- `.tsv`
- `.xls`
- `.xlsx`

### What Spatial Chart can do

- Open spreadsheet files from the system picker
- Receive supported files through Android `VIEW` intents
- Inspect multi-sheet Excel workbooks
- Select spreadsheet ranges using Excel-style references
- Assign data sources for **X**, **Y**, and **Z**
- Use row or column indices as axes when appropriate
- Render data in multiple 3D styles:
  - **Columns**
  - **Manifold**
  - **Point Cloud**
- Adjust visualization appearance with:
  - color schemes
  - color-by-range mapping
  - column geometry
  - surface interpolation
  - point radius
- Adjust interpretation with:
  - per-axis standardization
  - per-axis scaling
- Manipulate the scene with spatial gestures for:
  - rotation
  - translation
  - scaling

### Typical workflow

1. Launch **Spatial Chart** on a PICO OS 6 device.
2. Open a CSV or Excel file.
3. Pick the spreadsheet range to visualize as **Z** values.
4. Define **X** and **Y** from matching ranges, row indices, or column indices.
5. Confirm the selection.
6. Explore the resulting 3D chart in space.
7. Refine the result using the data, scale, and view panels.

## Repository Structure

This repository is organized as a multi-tool workspace, even though only one tool is currently implemented.

```text
.
├── SpatialChart/         # Current spatial spreadsheet visualization app
├── gradle/               # Gradle version catalog and wrapper configuration
├── build.gradle.kts      # Root build configuration
└── settings.gradle.kts   # Project module declarations
```

## Tech Stack

- **Kotlin**
- **Android / Gradle**
- **PICO Spatial SDK / UI libraries**
- **Apache POI** for Excel parsing

## Target Platform

- **PICO OS 6**
- **arm64-v8a**
- Android SDK 35 based project configuration

## Building

### Prerequisites

- Android Studio with Android SDK 35 support
- A PICO OS 6 compatible device or runtime environment
- Access to the required Maven repositories for the spatial dependencies used by the project

### Build with Gradle

From the repository root:

```bash
./gradlew :SpatialChart:assembleDebug
```

### Open in Android Studio

1. Open the repository in Android Studio.
2. Let Gradle sync complete.
3. Select the **SpatialChart** app configuration.
4. Build and deploy to a connected PICO OS 6 device.

## Project Direction

This repository is intentionally broader than the current implementation.

While **Spatial Chart** is the first tool, future additions may include other small utilities for spatially working with common file types and lightweight data/media formats.

The idea is not to build one monolithic application, but a set of clear, experimental, practical spatial tools.

## Status

This project is actively evolving.

- The repository concept is **multi-tool**
- The current implementation is **Spatial Chart**
- More spatial utilities are expected to be added over time

## License

See [LICENSE](./LICENSE).
