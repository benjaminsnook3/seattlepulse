# Seattle Pulse Backend

Spring Boot Kotlin backend service for Seattle Pulse.

## What it does

- Retrieves external data (live weather from Open-Meteo plus provider adapters for transit, sports, events, traffic).
- Normalizes incoming records into canonical backend models.
- Stores records in H2 via Spring Data JPA.
- Removes expired rows on a schedule.
- Calculates Seattle transportation impact from weather, transit, sports/events load, and traffic disruptions.
- Exposes a clean REST API for Android.
- Emits alerts and push notification events through a notification gateway.

## Endpoints

- GET /weather
- GET /transit
- GET /sports
- GET /events
- GET /traffic
- GET /dashboard
- GET /alerts
- POST /alerts (register device token)
- GET /area (location-aware view; optional lat/lon/neighborhood/radiusKm)

## Notifications

`AlertService` evaluates every ingestion cycle and emits push notifications for meaningful changes only:

- major Link disruption / major Metro disruption (CRITICAL priority)
- critical traffic incident (HIGH)
- game starting soon — within 90 minutes, in Seattle (NORMAL)
- large concert / major event starting soon (NORMAL)
- major event affecting transportation (via IMPACT)
- significant weather change — severity jump of two or more levels (HIGH)
- significant impact-score change — level change into HIGH/SEVERE (HIGH/CRITICAL)

**Spam prevention** (`NotificationPolicy`):

- Per-key cooldowns: CRITICAL 30 min, HIGH 60 min, NORMAL 3 hours.
- A CRITICAL escalation can bypass a lower-priority cooldown.
- At most 3 notifications per evaluation cycle, highest priority first.
- Impact/weather alerts fire only on *change* (tracked level/severity), not every cycle.

Example payloads:

```
🚈 Seattle Pulse
Link 1 Line disruption
Expect delays between Northgate and downtown.

🏟️ Seattle Pulse
Mariners game starts in 90 minutes.
Expect heavier transit around T-Mobile Park.
```

The push transport is behind `PushNotificationGateway` (currently a logging implementation; swap in FCM for production).

## Location Awareness

`UserAreaService` makes the app location-aware **without requiring location permission**:

- Device coordinates (`lat`/`lon`) resolve to the nearest of 17 Seattle neighborhoods.
- Manual selection: pass `neighborhood=Ballard` (or any label) instead of coordinates.
- No input at all defaults to Downtown Seattle — the core app never needs location.

`GET /area` returns a "Your area" view: neighborhood, center, radius, per-service Link/Metro status (Normal / Minor Delays / Delays / Major Disruption), nearby major events, nearby traffic, relevant transit alerts, and rain/condition. Events and traffic are filtered by distance (haversine, default 3 km radius); transit alerts are matched by area label.

```
GET /area                                  # Downtown default
GET /area?neighborhood=Capitol Hill        # manual selection
GET /area?lat=47.62&lon=-122.35&radiusKm=5 # device location
```

(The Android permission prompt itself lives in the app; the backend accepts optional coordinates and never requires them.)

## Seattle Impact Engine

`ImpactEngine` (service/ImpactEngine.kt) is the secret sauce. It analyzes transit disruptions, sporting events, concerts, public events, weather, and traffic incidents within a near-term window (6 hours) and produces a 0-100 transportation impact score.

Bands: **0-24 LOW, 25-49 MODERATE, 50-74 HIGH, 75-100 SEVERE** (`ImpactLevel`).

Each score carries per-category contributions plus human-readable `factors`, so instead of "Mariners game" the user sees:

```
HIGH IMPACT
Mariners game + Link delays + concert at Climate Pledge Arena
```

Contributions are capped per category (transit 40, sports 30, events 30, traffic 35) so one bad line cannot dominate. Away games and non-major events do not contribute.

## Concerts & Major Events

Event ingestion (`TicketmasterEventsProvider`) pulls upcoming events from the Ticketmaster Discovery API for an approved registry of major Seattle venues (`SeattleVenue`): Climate Pledge Arena, Lumen Field, T-Mobile Park, Seattle Center, Paramount Theatre, WAMU Theater, Seattle Convention Center, Benaroya Hall, Moore Theatre, and Mercer Arts Arena.

Each event stores date/time, venue, location, category, attendance estimate (when available), and source.

**Importance scoring** (`EventImportanceService`, 0-100) decides what counts as "major" so the app is not an endless list of random events:

- very large / large crowd (attendance estimate or venue typical capacity)
- known major venue bonus
- category weighting (pro sports, festivals, concerts, parades, large public gatherings)
- title keywords (championship, finals, citywide events like Bumbershoot or New Year's Eve)
- transit-sensitive location bonus

Events scoring >= 55 (or at a >= 15k-capacity venue) are `major: true`. Only major events are persisted and served on `GET /events` and the dashboard.

API key: set `TICKETMASTER_API_KEY` env var, or put it in `config/application-local.yml` (gitignored). Never commit a real key. Note: the Discovery API only honors the singular `venueId` param, so the provider queries each approved venue separately and dedupes.

## Seattle Sports

Live upcoming games for the five major Seattle teams, from the public ESPN team schedule API (`ESPNSeattleSportsProvider`):

| Team | Sport | ESPN team id |
|---|---|---|
| Mariners | MLB | 29 |
| Seahawks | NFL | 26 |
| Kraken | NHL | 124292 |
| Sounders | MLS | 9726 |
| Storm | WNBA | 14 |

Each game stores team, opponent, start time, venue, `homeAway` (HOME/AWAY), `eventStatus`, and `inSeattle` (derived from the venue city / known Seattle venues). Only future, non-completed games within `seattle.sports.lookahead-days` are ingested, and each provider's stale rows are deleted before refresh.

The dashboard includes a `tonight` list: Seattle-area games starting later today (Pacific time), ordered by start time, so the UI can render:

```
Tonight
⚾ Mariners — 6:40 PM
🏟️ T-Mobile Park
```

## Unified Transit System

Transit alerts from multiple agencies are normalized into a single `TransitAlert` model so the UI renders one list with a `serviceType` badge to distinguish providers:

- `LINK` - Sound Transit Link light-rail alerts (scraped from soundtransit.org service alerts page).
- `METRO` - King County Metro alerts from the official GTFS-RT alerts protobuf feed (`seattle.transit.metro-alerts-url`).
- `OTHER` - any future transit provider.

Each `TransitAlert` carries: `serviceType`, affected line, affected station/area, status, severity, start time, end time, description, and source URL. Expired alerts are removed automatically (cleanup job + per-provider refresh deletes stale rows before re-inserting).

`GET /transit` and `GET /dashboard` return only active alerts, each including `serviceType` for UI filtering/grouping.

Adding another agency: implement `TransitProvider` (set a unique `providerName` and `serviceType` on each DTO) and register it as a `@Component` - ingestion picks it up automatically.

## Android UI Connection

Use Retrofit in Android against the weather endpoint and map response directly into your UI state.

Base URL notes:

- Android emulator to local backend: http://10.0.2.2:8080/
- Physical device to LAN backend: http://<your-machine-ip>:8080/
- Installable debug APK using the live Render backend: `android\gradlew.bat assembleDebug -PseattlePulseDebugBaseUrl=https://seattlepulse.onrender.com/`
- Release builds require a real backend URL: run `android\gradlew.bat assembleRelease -PseattlePulseBaseUrl=https://your-api-host/` or set `SEATTLE_PULSE_BASE_URL` before building.
- Android clean checkouts require an Android SDK path via `ANDROID_HOME` or an untracked `android/local.properties` file containing `sdk.dir=...`.

Example Retrofit contract:

```kotlin
interface SeattlePulseApi {
  @GET("weather")
  suspend fun getWeather(): List<WeatherResponse>
}

data class ForecastPointResponse(
  val time: String,
  val temperatureCelsius: Double,
  val rainProbabilityPercent: Int,
  val condition: String
)

data class WeatherResponse(
  val id: String,
  val observedAt: String,
  val temperatureCelsius: Double,
  val rainProbabilityPercent: Int,
  val windSpeedKmh: Double,
  val condition: String,
  val forecastSummary: String,
  val forecastHighCelsius: Double,
  val forecastLowCelsius: Double,
  val forecastPeakRainProbabilityPercent: Int,
  val forecast: List<ForecastPointResponse>,
  val description: String,
  val severity: String
)
```

The backend caches weather responses and automatically falls back to cached weather when the provider fails.

Transit contract for the unified feed:

```kotlin
interface SeattlePulseApi {
  @GET("transit")
  suspend fun getTransit(): List<TransitResponse>
}

data class TransitResponse(
  val id: String,
  val serviceType: String, // "LINK" | "METRO" | "OTHER"
  val affectedLine: String,
  val affectedArea: String?,
  val status: String,
  val description: String,
  val severity: String,
  val startTime: String?,
  val endTime: String?,
  val updatedAt: String,
  val expiresAt: String?,
  val source: SourceResponse
)
```

Group or badge alerts in the UI by `serviceType` to distinguish Link, Metro, and other services.

## Weather Reliability

- Live weather provider: Open-Meteo current + hourly forecast.
- Internal normalization: provider response mapped into WeatherRecord with forecast summary, high/low, and hourly forecast points.
- Cache: in-memory weather cache with configurable TTL via seattle.weather.cache-ttl-seconds.
- Failure handling: provider errors are caught and fallback to cache is used; ingestion logs failures safely without crashing the app.

## Tests

- WeatherCacheServiceTest: success mapping and failure fallback behavior.
- IngestionServiceWeatherTest: weather ingestion stores data on success and does not throw on failures.
- SoundTransitLinkAlertsProviderLiveTest: live Link alerts from soundtransit.org.
- KingCountyMetroAlertsProviderLiveTest: live Metro alerts from the GTFS-RT feed.
- ESPNSeattleSportsProviderLiveTest: live upcoming games for the five Seattle teams.
- TicketmasterEventsProviderLiveTest: live major events at approved Seattle venues (runs when TICKETMASTER_API_KEY is set).
- EventImportanceServiceTest: major-event rules (venue size, category, keywords, transit sensitivity).
- TransitNormalizationServiceTypeTest: LINK/METRO/OTHER service type mapping.
- DashboardTransitActiveTest: dashboard shows only active transit alerts.
- DashboardTonightTest: dashboard `tonight` lists future Seattle games in order.

Request body for POST /alerts:

```json
{
  "token": "fcm-device-token",
  "platform": "android"
}
```

## Runtime behavior

- Scheduled ingestion: every 5 minutes.
- Scheduled cleanup: every 10 minutes.
- Initial warmup ingestion at startup.

## Config

Edit src/main/resources/application.yml:

- seattle.ingestion.fixed-delay-ms
- seattle.cleanup.fixed-delay-ms
- seattle.notifications.enabled
- seattle.weather.latitude
- seattle.weather.longitude
- seattle.transit.soundtransit-alerts-url
- seattle.transit.metro-alerts-url
- seattle.sports.espn-base-url
- seattle.sports.lookahead-days
- seattle.events.ticketmaster-api-key (env: TICKETMASTER_API_KEY)
- seattle.events.lookahead-days

## Run

For Render Web Service deployment:

- Build command: `./gradlew clean bootJar -x test`
- Start command: `java -jar build/libs/seattle-pulse-backend-0.0.1-SNAPSHOT.jar`
- Health check path: `/actuator/health`
- Set `TICKETMASTER_API_KEY` as an environment variable when Ticketmaster events are enabled.

If Gradle wrapper is available:

```bash
./gradlew bootRun
```

If Gradle is installed globally:

```bash
gradle bootRun
```

H2 console:

- /h2-console
