# Database

PAMGuard uses a relational database to store detection results, tracking data,
and configuration metadata. Two database back-ends are supported: **SQLite**
(default, recommended for most users) and **MySQL** (for multi-computer or
high-throughput deployments).

---

## Overview

| Setting | Description |
|---------|-------------|
| Database system | SQLite (file-based) or MySQL (server-based) |
| Database file / connection | Path to `.sqlite3` file or JDBC connection string |
| Auto commit | Write records immediately vs. batching (see below) |

---

## SQLite

[SQLite](https://www.sqlite.org/) stores all data in a single `.sqlite3` file.
It requires no separate server software, making it the best choice for typical
field deployments.

### Choosing a file

1. Open **Settings → Database**.
2. Select **SQLite** from the *Database System* drop-down.
3. Click **Browse** to choose an existing file, or type a new path.
   PAMGuard will create the file and all required tables automatically on first run.

### Auto commit

By default, *Auto commit* is **off**. PAMGuard batches database writes and
flushes them:

- Every three seconds  
- When the uncommitted record count reaches 10  
- When PAMGuard stops or exits  
- When the database structure changes  
- In Viewer Mode, before data are reloaded  

> **Warning:** Enabling *Auto commit* writes every record immediately, which can
> significantly reduce overall processing performance. Leave it off unless you
> have a specific reason to enable it.

You can force an immediate flush at any time via **File → Database → Commit
Changes**.

---

## MySQL

MySQL is suitable when:

- Multiple networked computers must share a single database.
- Very large data volumes require a dedicated database server.

### Connection settings

| Field | Description |
|-------|-------------|
| Host | Hostname or IP address of the MySQL server |
| Port | Default is 3306 |
| Database name | Name of the existing database schema |
| Username / Password | Credentials for the MySQL user |

PAMGuard will create tables automatically inside the named schema on first
connection, but it will **not** create the schema itself — create the database
in MySQL before connecting.

---

## Viewing the data

PAMGuard writes data using a standard relational schema. Any standard SQL
client can read the file:

- **SQLiteStudio** (recommended, free) — <https://sqlitestudio.pl/>
- **DB Browser for SQLite** — <https://sqlitebrowser.org/>
- Any JDBC-compatible tool for MySQL

---

## Tips

- Keep the `.sqlite3` file on a local SSD rather than a network drive for best
  performance.
- Back up the database file regularly; PAMGuard does not maintain its own
  automatic backup.
- If PAMGuard crashes before a commit the last few records may be lost — use
  a UPS or ensure *Auto commit* is on in power-unreliable environments.
