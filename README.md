# Interview Prep (open-devmaxx)

A free, fully offline Android app for technical interview prep: system design, CS fundamentals, and applied AI. It uses FSRS spaced repetition for about 10 minutes of steady review a day. No account, no server, no streaks, no timers.

## How it works

- **Daily mix.** Each day you get the cards due for review (up to a cap) plus a few new cards rotated across your active tracks. Cards over the cap wait for the next day, so a missed week never turns into a wall of reviews.
- **Quiz cards.** Each card is 2–4 multiple-choice questions (some "select all that apply"), with instant feedback and a short reason after each answer. After the last question you see your score, the card's key points, the explanation, and the source. The app suggests a rating (Again / Hard / Good / Easy) from the fraction you got right, and you can override it.
- **Calm UI.** No screen transitions or ripple animations.
- **Prereqs.** A card becomes eligible as new only after its prereqs have been seen.
- **Sources on every card.** Each card links the exact source section it came from and shows when it was checked. A flag button records "seems wrong / unclear", and Settings exports flags as JSON so the card can be re-checked.

## Content

Cards are generated on a laptop and bundled as JSON in `app/src/main/assets/content/`. The pipeline in [`pipeline/`](pipeline/README.md) grounds every card in a vetted free source, has a separate checker pass compare each card (including every right and wrong answer) against its cited passage, and validates the schema. Only cards the checker marked `verified` or `partial` ship in the app.

Current content: 31 System Design, 8 CS Fundamentals (TCP, transaction isolation), and 7 Applied AI (transformer architectures, LLM inference) cards.

Sources and licenses:

- [The System Design Primer](https://github.com/donnemartin/system-design-primer) by Donne Martin, CC BY 4.0
- [Computer Networks: A Systems Approach](https://book.systemsapproach.org) by Larry Peterson and Bruce Davie, CC BY 4.0
- [PostgreSQL Documentation](https://www.postgresql.org/docs/17/), PostgreSQL License
- [Hugging Face LLM Course](https://huggingface.co/learn/llm-course), Apache 2.0

Cards paraphrase and summarize these sources; changes were made.

## Build and install

Requires JDK 17 and the Android SDK (platform 35).

```bash
./gradlew assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the tests (unit tests plus Robolectric UI tests; screenshots land in `app/build/outputs/roborazzi/`):

```bash
./gradlew testDebugUnitTest -Proborazzi.test.record=true
```

## Stack

Kotlin, Jetpack Compose (Material 3), Room for progress, DataStore for settings, WorkManager for the optional daily reminder, kotlinx.serialization for content. FSRS-5 with default weights, scheduling in whole days.
