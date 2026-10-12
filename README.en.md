# PetStay · Pet boarding, home feeding and quote matching platform

[中文](README.md) | [English](README.en.md)

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/) · **Public source learning edition / non-commercial source edition**.

PetStay brings pet profiles, boarding and home visit requests, provider availability, quote selection, daily care records and two-party handover into one platform. Built with Java 21 / Spring Boot, Vue 3, MySQL 8 and Docker Compose, its mobile H5 and desktop operations interface use a real shared database. It supports learning about pet service marketplaces, service capacity, frozen order snapshots and private evidence, and can serve as a customization foundation after obtaining commercial authorization.

## Request to handover

1. Owners register, add private contact information and a cat or dog, and publish boarding or one fixed visit window per day.
2. Providers submit service information for operator review, then publish availability. Matching checks city/district, service type, species, weight, dates and actual capacity.
3. Providers issue fixed quotes valid for 48 hours. Owner selection rechecks eligibility and freezes price, pet and party details, creating an order and daily tasks atomically.
4. The assigned provider starts care, completes tasks, appends corrections and private photos, then submits handover. The owner accepts or disputes; responses remain in history.
5. Offline receipts/refunds preserve original references and enforce amount limits. Owners may review completed orders; only approved reviews become public. The platform never charges or holds funds.

## Implemented modules

| Interface | Features |
|---|---|
| Public mobile web | Actual approved service directory; area/date/type filters; details; approved reviews; Chinese/English switch |
| Pet owner | Registration/login, private profile, pets, request drafts/publication, matching candidates, quote selection, private orders, acceptance/disputes, reviews, CSV |
| Care provider | Service review, availability/capacity, eligible opportunities, quotes/withdrawal, daily tasks, appended corrections, private images, offline receipts/refunds |
| Operations | Provider/review moderation, scoped orders, reports and audit |
| Administration | Accounts, roles, teams, menu permissions, settings and dictionaries; live identity checks, protected public-member role and last administrator |

A member may be both owner and provider applicant. Service information review is not identity, veterinary, insurance or professional certification. Exact addresses, private contacts and care instructions are disclosed only to confirmed order participants and authorized operators.

## Actual running pages

Screenshots use an isolated TEST database. Names, addresses, contact notes and reviews are marked TEST; they are not real businesses or customers. The image validation fixture is a brand image explicitly labeled as not a pet photo.

### Account entry — members and providers share an account

![Login](docs/screenshots/02-login.jpg)

### Service directory — actual filters and reviewed information

![Service directory](docs/screenshots/01-market.jpg)

### Pet profiles — species, weight and private care instructions

![Pet profiles](docs/screenshots/03-pets.jpg)

### Request details — matching candidates and actual quotes

![Matching](docs/screenshots/04-matching.jpg)

### Care handover — tasks, private photos, history and frozen details

![Order handover](docs/screenshots/05-order.jpg)

### Operator moderation and reports

![Moderation](docs/screenshots/06-operations.jpg)

![Reports](docs/screenshots/07-reports.jpg)

### Roles and system settings

![Roles](docs/screenshots/08-roles.jpg)

![Settings](docs/screenshots/09-settings.jpg)

### Mobile H5 — browser viewport 390×844

![Mobile H5](docs/screenshots/10-mobile.jpg)

## Architecture and directories

Browser → same-origin Nginx proxy → Spring Boot business/authorization services → MySQL. Flyway applies migrations; JPA validates schema. Care images are decoded, stripped of EXIF and persisted in the database; backups retain images and frozen order snapshots. A platform team-row lock and version checks serialize business writes for one operator, one application instance and low-volume use.

```text
backend/                  Java 21 / Spring Boot 4.0.7, business and identity APIs
  src/main/resources/db/migration/  V1 identity, V2 pet matching and care
  src/test/               Rules, permissions, image safety and real HTTP integration
frontend/                 Vue 3.5.43 / Vite 8.1.5, mobile and desktop UI
  public/brand/           Official logo
scripts/                  Environment initialization, tests, backup, isolated restore, release checks
docs/                     Business, API, deployment, security, operations and H5 notes
compose.yaml              MySQL 8.4, backend, Nginx frontend
.env.example              Configuration names without actual credentials
LICENSE / NOTICE          Own non-commercial license / third-party notices
```

## Requirements and startup

Docker Engine, Compose v2 and Python 3.10+; recommended 4 CPU cores, 8GiB RAM and 10GiB free disk. Source development uses Java 21, Maven 3.9+, Node **24.19.0** and npm. First builds require access to official dependency registries.

From the repository root:

```sh
python3 scripts/init-env.py
docker compose -p petstay config --quiet
docker compose -p petstay up -d --build --wait --wait-timeout 240
```

Open http://127.0.0.1:8136/ ; health: http://127.0.0.1:8136/actuator/health . Initialization refuses to overwrite `.env`. Random passwords stay in the local `.env` (0600), are not printed or committed. Default administrator username is `admin`; read `ADMIN_PASSWORD` locally. **No fixed public password.** Environment values do not reset existing account passwords. Backend builds run tests; frontend builds run formatting, lint, tests and build.

### Database initialization and upgrades

A new MySQL volume creates the database, executes `V1__identity.sql` and `V2__pet_marketplace.sql`, then initializes administrator, roles, permissions, menus, dictionaries and settings. **No fabricated business seed data.** Migrations live at `backend/src/main/resources/db/migration/`; no separate repeatedly executed business initialization script. Back up and test restore before upgrades; add migrations instead of modifying applied versions. Hibernate only validates.

### Configuration

| Variable | Meaning |
|---|---|
| MYSQL_ROOT_PASSWORD / DATABASE_PASSWORD | Unique strong database passwords generated by initialization |
| ADMIN_USERNAME / ADMIN_PASSWORD | Initial administrator; password 12–72 UTF-8 bytes with upper/lowercase and digits |
| WEB_PORT / BIND_ADDRESS | Defaults 8136 / 127.0.0.1; avoid other project ports |
| COOKIE_SECURE | false locally; must be true for public HTTPS |
| DATABASE_URL / DATABASE_USER | Optional external MySQL; certificate validation and trusted CA required, see deployment notes |

## Tests and validation

```sh
mvn -B -f backend/pom.xml spotless:check clean verify
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
python3 -m venv .venv
.venv/bin/pip install -r scripts/requirements-quality.txt
.venv/bin/python -m black --check scripts
.venv/bin/python -m py_compile scripts/*.py
python3 scripts/release-check.py
git diff --check
```

Only against an isolated empty TEST instance, with a restricted state file:

```sh
python3 scripts/smoke.py --base http://127.0.0.1:8136 --mode seed --state /private/tmp/petstay-quality-state.json
```

The script verifies registration privilege rejection, matching and quotes, concurrent selection, capacity, snapshots, states, task corrections, private images, disputes, original receipt refunds, reviews and authorization. The state file contains temporary credentials and must never be published. `capture`/`compare` check all visible business data and image hashes across restart and restore; see [validation record](docs/verification.md). Executed checks include 32 backend tests, 10 frontend tests and 233 MySQL workflow assertions. These do not certify public deployment, physical devices or production load.

## Deployment, backup and troubleshooting

[Deployment and restore](docs/deployment.md) covers HTTPS, an independent domain, configuration, private backups and isolated restore. Backups contain private data; the script briefly stops the named backend and retains image bytes. Never upload backups publicly. Weak passwords and port conflicts prevent startup; inspect this project's logs. Registry/network failure should be diagnosed without deleting original data volumes. Sessions expire after 30 minutes or application restart. On version conflict, refresh and review before resubmitting.

Responsive mobile layout is implemented. A prepared public account, HTTPS domain and actual WeChat device verification allow connecting an H5 link. **No public deployment, WeChat login/payment or native mini program has been delivered.** See [mobile H5 and WeChat](docs/wechat-H5.md).

## Security and known limitations

BCrypt, HttpOnly sessions, CSRF, live identity/role checks, registration/login throttling, versions and private image/CSV authorization are implemented. Images: ≤2MiB, 6000px edge, 9 million pixels and 20 per order. Administrators cannot impersonate either party. See [security](docs/security.md) and [business rules](docs/business.md).

One request covers one cat/dog, one area and one service type. Boarding: 1–30 nights; visits: 1–14 days with one fixed morning/afternoon/evening window per day. Area matching has no GPS distance or AI. Early finish does not recalculate the fixed quote, and capacity remains reserved until acceptance. No SMS, live chat, online payments/escrow/split settlements, insurance, medical care or identity verification. In-memory sessions, a platform write lock and list limits require evaluation before multiple instances or heavy load. Production readiness and real-user operating metrics are unverified.

## License, contributions and feedback

Personal learning and exchange only. Commercial use requires written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. **Public source, non-commercial use**; this is not an MIT/Apache free commercial-use license. README, product notice and [LICENSE](LICENSE) agree; third-party rights remain in [NOTICE](NOTICE). No medical, service-quality or commercial operating guarantee. Commercial operation/deployment requires separate authorization.

Use Issues/PRs for reproducible problems with version, steps and sanitized logs; read [AGENTS.md](AGENTS.md) before modifying. Report security issues privately, without posting keys, customer data or exploit details publicly.

## Contact ZhiHua Technology

This public source learning edition supports personal learning, technical research and non-commercial exchange. Commercial licensing, enterprise information systems, SME digital/AI transformation, private deployment, software outsourcing and implementation, FDE outsourcing, OPC support and deep customization are available through ZhiHua Technology.

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)

For commercial authorization or deep customization, contact ZhiHua Technology.
