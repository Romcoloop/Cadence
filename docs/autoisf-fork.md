# AutoISF fork: blended BG, the tuning card and easy tuning

Cadence, a build of AndroidAPS (github.com/Romcoloop/Cadence), changes the **OpenAPS AutoISF** plugin in
five places. Everything else is upstream AndroidAPS.

| What | Where in the app | Off means |
|---|---|---|
| 1. Blended BG for bg_ISF | AutoISF settings → Blended BG ISF weight | weight 0.0 = as upstream |
| 2. AutoISF log analysis (the tuning card) | card on the AutoISF plugin screen | reads only; writes on "Apply" |
| 3. Steps and activity | AutoISF settings → Steps and activity | all 5 switches off = as upstream |
| 4. SMB always with any sensor | AutoISF settings → SMB always with any sensor | switch off = as upstream |
| 5. Easy tuning: two sliders instead of the 15 numbers | AutoISF plugin settings → Easy tuning | switch off = the full settings are used |

All of it is in the branch `main`. **Install builds from `main`.**
Build: GitHub Actions workflow "AutoISF APK" (phone and wear APK; `runTests = true` runs the APS unit
tests first).

> **Before you install a new APK:** Maintenance → Export settings.
> Nothing here changes a value on its own. Every value is set by you: in the settings, or with the
> "Apply" button of the analysis card after a confirmation dialog.
> This is insulin dosing. Change one value at a time, in small steps, and talk to your diabetes team.

**Credits.** The AutoISF algorithm is ga-zelle's, as in upstream AAPS. The step rules of chapter 6
are ported from **OpenAPS AIMI** by MTR93600 (https://github.com/MTR93600/OpenApsAIMI,
`DetermineBasalAIMI2.kt`), with AIMI's thresholds. The blended BG idea (chapter 1) comes from
**Boost**. Everything, this fork included, is AGPL-3.0.

## 1. Blended BG

`bg_ISF` normally looks at the current BG only, so it reacts late when BG rises fast. With a weight
above 0 the BG used for `bg_ISF` is moved up by a part of the rise the parabola expects in the next
20 minutes: `BG + weight × (predicted BG − BG)`. The idea comes from Boost.

Rules in code (`BlendedBg.kt`):

- only when the parabola fit is good (correlation ≥ 0.9, the same limit acce_ISF uses),
- only at or above target, and only when BG rises; falling BG always uses the real BG,
- the lift is capped at 36 mg/dl (2 mmol/l), the weight at 0.5,
- it changes `bg_ISF` only; `acce_ISF`, `pp_ISF` and `dura_ISF` are not touched,
- the final ISF is still limited by `autoISF_min` / `autoISF_max`.

The log shows it as `bgBlend` lines. A weight of 0.1–0.2 is a careful start.

There is one set of AutoISF and SMB delivery values, and the loop uses it all day. Earlier builds
had meal slots (own values for breakfast, lunch, dinner, the weekend and the night, chosen by the
clock). They were removed: the values stored in the slots are not used any more.

## 2. The tuning card: AutoISF log analysis

The card on the AutoISF plugin screen reads the log files on the phone (`AndroidAPS.log` and the
zipped older ones) and shows what AutoISF did in **the last hours**, whatever happened in them. It
never changes a value by itself.

### 2.1 Controls

- **Hours:** a slider from 1 to 24 hours, and two quick buttons **3 h** and **6 h**. You do not type
  anything. The label shows the chosen hours. Changing it clears the result on screen.
- **Analyze** runs it.
- The time looked at is the chosen hours before now. There are no meal times and no search for a
  meal: simply the last hours, with everything in them.
- **Apply** writes to the AutoISF settings.
- If the settings stored now are not the ones the last loop run used (a setting was changed after
  that run, or the run is from an older build that used a meal slot), there is **no suggestion**:
  it would start from the wrong number. Run the analysis again after the next loop run.
- The result stays on screen until you change the hours or press Analyze again.

### 2.2 What it shows

The title says the hours looked at (*Last 6 h*) and from when to when.

**Facts:** the peak (BG, time, how far above target), minutes above target +2 and +4 mmol/l, minutes
below 3.9, BG above target at the end, SMB count and units, how often `autoISF_max` cut the factor,
how often the SMB was as big as maxBolus allowed, the factor that drove ISF most.

**Values in use now:** all 15 AutoISF and SMB delivery numbers the loop uses now. Where the rules
suggest a change, the line shows it: `1.60 → 1.50`.

**Events:** every SMB in these hours that was followed by a low (below 3.9) within 3 hours, or by a
predicted low that stopped SMBs (`minGuardBG`), with the factor that drove that SMB.

If there are less than 6 loop runs in the chosen hours, the card says so. If the log does not cover
all of the time (the loop stopped, the log began late) or a temp target was active, the facts are
shown with a note, but there is **no suggestion**.

### 2.3 How a suggestion is made

Rules, no model, and they see only the chosen hours. Nothing is suggested from less than 2 hours of
runs with the same settings, and a change of a setting inside that time starts a new period. Each
suggestion has its own **Apply** button. They come from the same few events, so apply **one at a
time** and watch.

*Too much insulin* – when there were at least 2 lows after SMBs, or more than 4 % of the time below
3.9, or enough predicted lows: look at the SMBs before the lows. If at least half of them …

| … were | suggestion |
|---|---|
| cut by `autoISF_max` | `autoISF_max` − 0.1 |
| driven by acce_ISF | `bgAccel_ISF_weight` × 0.8 |
| driven by bg_ISF | `higher_ISFrange_weight` × 0.8 |
| at the maximum SMB delivery ratio | `smb_delivery_ratio_max` − 0.1 |
| as big as maxBolus allowed | `smb_max_range_extension` − 1 |

*Too little insulin* – only when there was no low and no event at all, more than 25 % of the time
above 10 mmol/l, and at least 24 runs high with the prediction still above target:

| … | suggestion |
|---|---|
| `autoISF_max` cut the factor in ≥ 30 % of those runs | `autoISF_max` + 0.1 |
| else bg_ISF drove them in ≥ 30 % | `higher_ISFrange_weight` × 1.1 |

Every suggestion names the number of events behind it, the value before and after, and one reason.
**Apply** asks first (the dialog says that the suggestion comes from a few hours only), writes the
value, and refuses if the value changed meanwhile. Values are clamped to the limits of the setting.

### 2.4 How to tune with it

1. A few hours are a sample of one. A meal can go wrong because of the carbs, a walk, a late bolus or
   a sensor. Do not change a value because of one bad time. Look at the same part of the day on the
   next days and change something only when the same thing repeats.
2. Choose the hours so that they fit what you look at: for a lunch at 12:00 look at 3 hours at about
   15:00. The values are the same all day, so a change made because of lunch also acts at breakfast,
   at dinner and in the night.
3. Apply one suggestion, then watch the next days.
4. Lows always win over highs: when there are lows, give less insulin first, even when there are
   also highs.
5. There are no statistics over many days, no whole-day view and no history of the changes. Write
   down what you changed (the log also has a line for each Apply).

## 3. Starting values

The table shows the values of the author's own setup, read from his log of 2026-10-27 (14:09),
next to the AutoISF defaults. They are **one person's values**, not a recommendation for anyone else.

| Setting | Author | AutoISF default | Note |
|---|---|---|---|
| autoISF_max | 1.6 | 1.0 (off) | 1.6 is already strong: ISF can be made 60 % stronger. |
| autoISF_min | 0.6 | 1.0 (off) | ISF can be weakened to 60 % when BG falls. |
| bgAccel_ISF_weight | 0.3 | 0.0 | Reacts to a fast rise. On 2026-10-27 acce_ISF hit the 1.6 cap. |
| bgBrake_ISF_weight | 0.08 | 0.0 | Brake on a fast fall. |
| pp_ISF_weight | 0.09 | 0.0 | Post-prandial rise. |
| lower_ISFrange_weight | 0.3 | 0.0 | Below target: weaker ISF. |
| higher_ISFrange_weight | 0.6 | 0.0 | Above target: stronger ISF. |
| dura_ISF_weight | 0.03 | 0.0 | Long plateaus above target. |
| Blended BG ISF weight | set by hand | 0.0 | Fork only (chapter 1). 0.1–0.2 is a careful start. |
| iob_threshold_percent | 90 % | 100 % | Above this share of max IOB no SMB. |
| smb_delivery_ratio | 0.5 | 0.5 | |
| smb_delivery_ratio_min | 0.6 | 0.5 | |
| smb_delivery_ratio_max | 0.8 | 0.5 | 0.8 = up to 80 % of the insulin needed in one SMB. |
| smb_delivery_ratio_bg_range | 0.25 | 0.0 | In mmol/l when below 10. |
| smb_max_range_extension | 5.0 | 1.0 | With an even target this allows an SMB of 5 × 30 min of basal (about 1 U at 0.39 U/h). This is what made the 0.95 U SMB on 2026-10-27. |
| SMB on even target | on | off | Together with the line above. |

Also from that log, for context: max IOB 6 U, max basal 1.9 U/h (≈ 5 × basal 0.39), autosens max 1.6,
maxSMBBasalMinutes 30, UAM 30, target 6.2 mmol/l, ISF 2.27 mmol/l/U, CR 12 g/U.

## 4. What is planned

1. Night metrics in the analysis: lowest BG, the course 23:00 → 03:00 → morning, and a clear line
   between "the basal in the profile is wrong" and "an AutoISF value should change".
2. Suggestions from a small rule table (peak, time to peak, lows early / late), with history and
   "Undo".
3. Automatic small changes with hard limits in code, after enough meals (3 of the last 5), with a
   global off switch.
4. Optional "Explain with Claude": the app sends only the summary and the suggestion, never raw logs,
   and the model never decides a dose.
5. Later ideas: steps and heart rate from the watch as an activity signal (log first, act later),
   a manual stress / sick-day mode with its own 15 values.

## 5. For developers

- `plugins/aps/.../openAPSAutoISF/BlendedBg.kt` – chapter 1.
- The keys of the removed meal slots (`autoISF_slot_*`, `autoISF_meal_window_*`, `autoISF_slots_enabled`,
  `autoISF_weekend_slots_enabled`, `autoISF_holiday_*`) are gone from the code. Values an older build
  stored under them stay in the preferences of the phone and are not read.
- `logAnalysis/AutoIsfLogParser.kt` – one run per `Result: RT(...)` line, settings from the
  `OapsProfileAutoIsf(...)` line. `AutoIsfLogAnalyzer.kt` – statistics, events and the rule table of
  2.3. `AutoIsfRecentAnalyzer.kt` – the numbers of the last hours. `AutoIsfLogAnalysis.kt` – the analysis and
  "Apply".
- The unit tests of the APS module run in the "AutoISF APK" workflow with `runTests = true`. The
  preference schema snapshot (`app/src/testFull/resources/prefs-schema.txt`) is kept by hand and
  sorted.

## 6. Steps and activity

Five switches in AutoISF settings → **Steps and activity**. All are off; off = as upstream. The rules
are ported from OpenAPS AIMI (MTR93600/OpenApsAIMI, AGPL-3.0) and **only reduce**: they never add an
SMB or basal. Without step data nothing fires.

| Switch | What it does |
|---|---|
| Use the phone as step source | Picks **one** source for all step rules, never a mix. **On:** only the phone's step sensor. The phone also stores its steps in the database the way a watch does (5/10/15/30/60/180 minute rows, once a minute), so the upstream automation trigger "Steps count" works without a watch. Needs the **Physical activity** permission; the app asks for it in the permissions sheet. **Off:** only the steps sent by your watch. If the chosen source has no fresh data (under 15 minutes old), the step rules do nothing; there is no silent switch to the other source. An older watch app sends its 10 and 15 minute values as single 5 minute blocks and the app adds the blocks up; a watch app from AAPS #5499 on sends sums and the app takes them as they are. The app sees which form the watch sends, so both sources read the same way, also when the phone and the watch do not have the same version. A watch counts finished 5 minute blocks, so its newest value can be up to 5 minutes old. |
| Sport guard | Three levels from the steps (mild, guard, long). While you move: temp basal **0 %**, SMB 0 (mild: SMB × 0.8). After you stop: a short hold, then SMB and temp basal come back to 100 % slowly (30 to 120 minutes). The step limits of the levels are five settings under the switch (see 6.1). |
| Longer SMB interval after activity | After > 1500 steps in 3 h with BG < 120 mg/dl (6.7) at least 10 min between SMBs; while still moving (> 100/5 min and > 500/30 min, last SMB older than 20 min) at least 15 min. |
| Morning: SMB only after waking | Between the start and end hour (default 03:00–10:00) **no SMB until N steps are counted within one hour** (default 150, a setting). While asleep a dawn rise is corrected by temp basal only (as with SMB off). Waking is a latch: once open, the gate stays open until the end hour, also while sitting still at breakfast. Outside the window the latch is reset. Without step data nothing is blocked. Steps do not tell dawn from a meal; they tell sleep from being awake. Once awake, the rise shape (acce_ISF) and a declared meal decide. |
| Effort protection | From 25 steps/min the SMB gets smaller, at a brisk walk (40/min and more) down to ×0.45. After the walk the protection fades over 30 min (a short walk) up to 120 min (30+ min of walking). A short burst (5 minute window only) reduces the SMB now but does not start the long protection; that needs the 15 minute window. |

The loop log has a line `steps: steps (watch) 5m=… 10m=… 30m=… 60m=… 180m=…; …` (`phone` when the phone is the source; all windows are sums) and the reason carries
`afterglow …` and `sport guard: …` (see 6.1), `effort x0.62: SMB 0.80 -> 0.40U` or `Waiting …`.

A safe start: first only the step source (the switch **Use the phone as step source**), look at the step graph and the `steps:` log
line for a day or two. Then **Effort protection** (the mildest, gradual), then **Sport guard**. The
upstream automation with the trigger "Steps count" + the action "Start temp target" replaces AIMI's
"target 130 while active" rule, so that rule is not here.

For developers: `openAPSAutoISF/steps/` – `StepBuckets.kt` (minute buckets, window sums),
`PhoneStepCounter.kt` (interface; `AndroidPhoneStepCounter` in androidMain, iOS and desktop answer
"not available"), `AutoIsfSteps.kt` (store to the database, read, memory), `StepsActivityRules.kt`,
`EffortActivityBelief.kt` and `WakeGate.kt` (pure rules, tests in `commonTest`). `determine_basal` takes
`steps_activity: StepsActivity`. Keys `autoISF_steps_*` in `BooleanKey` and `IntKey`. `AutoIsfSteps.windows` picks the source (`StepSource`) and reads the watch rows as sums: `watchSendsSums` tells the two watch forms apart, `StepWindows.fromWatchBlocks` adds up the blocks of an older watch app and `StepWindows.fromWatchSums` takes the sums of a newer one. The sport guard is `ActivityAfterglow.kt`
(pure, with its memory kept in `AutoIsfSteps`); the temp basal limit runs after the dosing in
`DetermineBasalAutoISF.limitBasalBySteps`.

### 6.1 Sport guard in detail

The old guard let go at once when there were 0 steps in 5 minutes, and the dosing jumped back to
100 %. Now it has three levels and a slow way back.

The step limits are settings under **Sport guard** (the numbers below are the defaults). Each one has
a range, so a typing mistake cannot switch the guard off. A higher limit starts the guard later, so
there is less protection. The levels are checked from the highest down; a limit set below a lower
level only makes that lower level skipped. The fast start (400 in 5 min and 800 in 10 min) and the
"moving now" test (100 steps in 5 min, 25 steps/min over 15 min) are fixed.

| Setting | Default | Range |
|---|---|---|
| Mild: steps in 60 min | 2500 | 1000 – 5000 |
| Strong: steps in 30 min | 2000 | 500 – 4000 |
| Strong: steps in 60 min | 4000 | 1000 – 8000 |
| Long: steps in 60 min | 6000 | 2000 – 12000 |
| Long: steps in 180 min | 7000 | 2000 – 20000 |

| Level | Starts with (step sums) | While you move | After you stop |
|---|---|---|---|
| Mild | ≥ 2500 steps in 60 min | basal 0 %, SMB × 0.8 | no hold; SMB and basal go from 80 % to 100 % in 30 min |
| Guard | ≥ 2000 in 30 min, ≥ 4000 in 60 min, or ≥ 400 in 5 min **and** ≥ 800 in 10 min | basal 0 %, SMB 0 | 10 min hold (basal 0 %, SMB 0), then 50 % to 100 % in 60 min |
| Long | ≥ 6000 in 60 min or ≥ 7000 in 180 min | basal 0 %, SMB 0 | 10 min hold, then 50 % to 100 % in 120 min |

"Basal 0 %" is a zero temp basal. In the way back, "basal 50 %" means the temp basal may not be above
50 % of the profile basal. A lower temp stays as it is. The limit only makes the basal smaller.

Example, a 40 minute walk (guard level), 12:00 is the last step:
12:00–12:10 hold, no basal, no SMB · 12:10 SMB and basal at 50 % · 12:40 at 75 % · 13:10 back at 100 %.

Good to know:
- **How the level is chosen:** the level is taken at the time of the movement and kept in a memory.
  After the walk the 60 and 180 minute sums stay high for hours, so they are not used for the way
  back. A new level during the same way back can only be higher.
- **Moving or not:** "moving now" needs at least 100 steps in the last 5 minutes **and** a real pace of
  25 steps/min over 15 minutes. This is the same for a new start and for a walk that goes on or starts
  again during the way back. A new start also needs a level from the sums. A few steps at home (the
  kitchen, the toilet) never start the hold, also when the 60 and 180 minute sums are still high from
  a walk long ago.
- **High and rising glucose:** above 10 mmol/l (180 mg/dl) and rising, the way back is half as long,
  so a meal is not blocked for an hour.
- **Low or falling glucose:** under 4.5 mmol/l (81 mg/dl) or a fall of 5 mg/dl in 5 minutes or more,
  the SMB stays 0 during the way back. The basal still follows the ramp.
- **With Effort protection:** the stricter SMB factor of the two counts. They are not multiplied.
- **Bad glucose data:** if the run gives up (old data, noise, flat line), the guard does not set a zero
  temp. The loop never suspends on bad data.
- **Memory:** it is kept in the app only. After a restart of the app there is no way back, the
  guard starts again with the next walk.
- **No step data:** the guard does nothing and the dosing is at 100 %, also in the middle of a walk
  when the phone sensor stops. The memory is kept for the time of the gap.
- **No escape for high glucose while you move:** during the movement and the hold, the temp basal stays
  at 0 % and the SMB at 0 even when glucose is high. The "high and rising" rule works only in the way
  back. Watch it on a long walk or a run with a high start.
- The numbers are in the code (`ActivityAfterglow.kt`), not in the settings. Tell me if one of them is
  too strong or too weak after a few days of the log.

In the log (loop reason):
`afterglow GUARD: moving, basal 0%, SMB 0` · `afterglow GUARD: hold 4/10m, basal 0%, SMB 0` ·
`afterglow GUARD: taper 20/60m, x0.67` (+ `high and rising, half length` or `low or falling, SMB 0`) ·
`sport guard: temp basal 1.2 -> 0U/hr` (the basal limit changed the temp) · `sport guard: no SMB`.

## 7. SMB always with any sensor

AAPS lets **Enable SMB always** and **Enable SMB after carbs** work only with a glucose sensor that
filters its values itself (Dexcom G6 and G7, Libre 2, Libre 3, Syai Tag). With any other sensor these two
are off and hidden, and an SMB is given only with carbs on board or with a low temp target.

The switch **SMB always with any sensor** takes this limit off. It is in the AutoISF settings (and in the
OpenAPS SMB settings), above "Enable SMB always", and it is **off by default**. This is not a change of
AutoISF, it changes a safety rule of AAPS.

- **It works at once:** "Enable SMB always" and "Enable SMB after carbs" are on by default, and with a
  sensor that is not on the list they were hidden until now. So SMB always is active from the moment you
  turn the switch on. Look at the two settings right after, and turn off the one you do not want.
- **Risk:** a sensor without filtering can show a jump that is not real. An SMB given for it can not be
  taken back, as a temp basal can. That can end in a low.
- **Before you turn it on:** know your sensor well, and use glucose smoothing (exponential or average
  smoothing in the configuration).
- **In the loop:** while the switch is on and the sensor is not on the list, the constraints of every run
  say `Safety: SMB always and after carbs allowed by the user though the active BG source has no advanced
  filtering`.
- **Simple mode** uses the default, so the limit stays on there.

## 8. Easy tuning

AutoISF has 15 numbers. Easy tuning replaces them with **two sliders**, for people who do not want
to learn all of them. It is off by default, and off means exactly as before.

**Where:** settings of the OpenAPS AutoISF plugin, at the top: the switch **Easy tuning**, and under
it the sliders **Rise** and **Plateau**. AAPS **Simple mode** hides all AutoISF settings, so switch
Simple mode off first, in the AAPS preferences. In Simple mode easy tuning is always off.

| Slider | What it means | Left | Right |
|---|---|---|---|
| Rise | how hard the loop acts while glucose climbs | milder | harder |
| Plateau | how hard the loop acts while glucose stays high | milder | harder |

Each slider has nine steps, from −4 to +4. The middle (0) is the start.

**While easy tuning is on:**

- The loop takes the 15 numbers from a table in the code (`AutoIsfEasy.kt`) and the two steps. The
  sections *AutoISF settings* and *SMB delivery settings* are hidden.
- Nothing is written into your settings. **When you switch easy tuning off, the values from the
  full settings apply again, not the sliders.** If you never set them, they are the AutoISF
  defaults, and with those AutoISF does almost nothing.
- The AutoISF weights are used even when the switch *Enable AutoISF weights* is off.
- The log has a line such as `AutoISF easy tuning: rise +1, plateau 0`.
- The analysis card (chapter 2) shows the values in use, but makes no suggestion.

**The table.** Slider *Rise*:

| Step | −4 | −3 | −2 | −1 | 0 | +1 | +2 | +3 | +4 |
|---|---|---|---|---|---|---|---|---|---|
| bgAccel_ISF_weight | 0.05 | 0.08 | 0.10 | 0.12 | 0.15 | 0.19 | 0.22 | 0.26 | 0.30 |
| pp_ISF_weight | 0.010 | 0.015 | 0.020 | 0.025 | 0.030 | 0.035 | 0.040 | 0.050 | 0.060 |
| bgBrake_ISF_weight | 0.12 | 0.12 | 0.11 | 0.11 | 0.10 | 0.10 | 0.09 | 0.09 | 0.08 |

Slider *Plateau*:

| Step | −4 | −3 | −2 | −1 | 0 | +1 | +2 | +3 | +4 |
|---|---|---|---|---|---|---|---|---|---|
| higher_ISFrange_weight | 0.20 | 0.25 | 0.30 | 0.35 | 0.40 | 0.45 | 0.50 | 0.55 | 0.60 |

*Plateau* moves only the weight for the height of glucose. It starts to act about 1.1 mmol/l
(20 mg/dl) above target. With glucose 2.8 mmol/l (50 mg/dl) above target it makes ISF 8 % stronger
at step −4, 15 % at step 0 and 23 % at step +4.

Fixed, no slider changes them:

| Setting | Value | Note |
|---|---|---|
| autoISF_max | 1.4 | the limit of every factor |
| autoISF_min | 0.7 | |
| lower_ISFrange_weight | 0.3 | |
| dura_ISF_weight | 0.03 | how long a plateau lasts; not on the slider, because on a plateau the height of glucose nearly always decides |
| Blended BG ISF weight | 0.0 | off |
| iob_threshold_percent | 80 % | works only with *SMB on even target* on, as always |
| smb_delivery_ratio_min | 0.5 | at target and below |
| smb_delivery_ratio_max | 0.8 | from 0.5 mmol/l above target |
| smb_delivery_ratio_bg_range | 0.5 mmol/l | |
| smb_max_range_extension | 2 | an SMB of up to 2 × 30 min of basal |

**Read this before you use it:**

- The numbers are a careful start. Nobody has tested them as a dosing for you. Start at 0 / 0, move
  one slider one step at a time, and watch a few days.
- "Milder" means a milder change of ISF. The SMB part is fixed and it is **stronger than plain
  AAPS at every step**: up to 80 % of the insulin needed in one SMB (AAPS: 50 %), and an SMB as big
  as 60 minutes of basal (AAPS: 30 minutes).
- The switches of AutoISF (SMB, UAM, *SMB on even target*, the step rules, ...) stay as you set
  them. Easy tuning changes only the 15 numbers.

For developers: `AutoIsfEasy.kt` (the table), `AutoIsfValues.kt` (the one place that reads the 15
numbers: table or settings), keys `autoISF_easy_mode`, `autoISF_easy_rise`, `autoISF_easy_plateau`,
the rule `AutoIsfVisibility.FULL_SETTINGS` in `core/keys` that hides the full settings.
