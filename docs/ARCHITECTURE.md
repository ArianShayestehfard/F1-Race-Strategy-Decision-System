# Architecture — F1 Race Strategy & Decision Support System

## Big picture

Three components, one database, one direction of truth:

```
                ┌───────────────┐
                │    FastF1     │  (data source, CSV/JSON over HTTP)
                └──────┬────────┘
                       │  ingest
                       ▼
┌──────────────┐   ┌────────────────────────┐   ┌──────────────┐
│   JavaFX     │◄──┤      Spring Boot       │◄──┤   Python     │
│  (desktop    │   │   REST API + JPA       │   │   Engine     │
│   client)    │──►│                        │──►│  (FastAPI)   │
└──────────────┘   └───────────┬────────────┘   └──────┬───────┘
                               │  JPA / JDBC           │  analysis
                               ▼                       │  simulation
                       ┌──────────────┐                │  ML
                       │    SQLite    │◄───────────────┘
                       │   race.db    │  direct write (ingest)
                       └──────────────┘
```

## Components

| Component | Tech | Responsibility |
|---|---|---|
| **Python Engine** | FastF1, Pandas, NumPy, Scikit-learn, FastAPI | Data ingestion, statistical analysis, race simulation, ML, strategy recommendation |
| **Backend** | Java 25, Spring Boot 3, Spring Data JPA | Manager: owns the DB, serves REST API, calls Python over HTTP, returns results to UI |
| **Desktop client** | JavaFX | Race selection, driver selection, run analysis/simulation, show strategy recommendation |
| **Database** | SQLite (`race.db`) → PostgreSQL later | Single source of truth for all race data |

## Request flow (example: "Simulate Race")

```
JavaFX  →  Spring Boot  →  SQLite (read race data)
                        →  Python POST /simulate (HTTP, JSON)
                        ←  result JSON
        ←  REST JSON  ←
```

The Java side never does analysis or prediction. The Python side never
serves UI traffic. SQLite is written by Python (ingest) and read by Java.

## Project layout

```text
backend/         Spring Boot application (REST API, JPA entities, services)
python-engine/   FastAPI service: ingest / analyze / simulate / predict / recommend
javafx/          JavaFX desktop client
database/        schema.sql, migrations, race.db (runtime artifact)
docs/            architecture, API contract, diagrams
tests/           integration tests (pytest)
```

## Key design decisions

1. **SQLite first, PostgreSQL later.** Same logical schema; JPA and
   SQLAlchemy-style access keep the swap cheap.
2. **Python as a service, not a script.** Spring calls `POST /simulate`
   etc. — see [API_CONTRACT.md](API_CONTRACT.md). No subprocess coupling.
3. **Millisecond integers everywhere.** No floating-point time across the
   API boundary.
4. **Decision-first.** Every feature must answer a real team decision
   ("pit now or not?"), otherwise it is cut — see README.
