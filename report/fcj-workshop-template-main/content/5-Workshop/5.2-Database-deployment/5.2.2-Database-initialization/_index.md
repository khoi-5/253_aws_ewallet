---
title: "Initialize the database"
date: 2024-01-01
weight: 2
chapter: false
pre: " <b> 5.2.2. </b> "
---

## Objective

Initialize the Cloud E-Wallet schema, initial administrator account, and service catalog after Amazon RDS for MySQL is available. RDS remains in private subnets, and the scripts run from the backend EC2 instances over the internal network.

## Execution scope

| Environment | Work |
| --- | --- |
| Local machine | Review SQL scripts and prepare a private administrator seed that is excluded from Git |
| Production EC2 | Connect to RDS and run the initialization scripts |

The team does not make RDS public for data import. Its security group accepts MySQL TCP `3306` only from the backend EC2 security group.

## Prepare the scripts

Production scripts are stored in `database/rds/` and must run in this order:

| Order | Script | Purpose |
| --- | --- | --- |
| 1 | `001_schema.sql` | Create tables, keys, indexes, constraints, and relationships |
| 2 | Private copy of `002_admin_template.sql` | Create the initial administrator account and profile |
| 3 | `003_services_seed.sql` | Insert the five default application services |

The order matters because the administrator and service seeds depend on tables created by `001_schema.sql`.

The `utf8mb4` schema contains seven main tables:

- `users`
- `account_tokens`
- `user_profiles`
- `admin_profiles`
- `wallets`
- `services`
- `transactions`

## Prepare the administrator seed safely

`002_admin_template.sql` contains placeholders and is tracked in the repository. The team creates a private copy:

```text
database/rds/002_admin_local.sql
```

This copy contains the administrator's phone number, full name, and BCrypt password hash. The plaintext password is never written to the script.

The repository includes this rule:

```gitignore
database/rds/*_local.sql
```

Therefore, `002_admin_local.sql` is not committed. Its contents, administrator information, and password hash are also excluded from screenshots and documentation.

## Transfer scripts to EC2

Using SFTP over the SSH connection, the team transfers the three required scripts to:

```text
/home/ec2-user/sql/
```

Verify the files on EC2 before execution:

```bash
ls -l /home/ec2-user/sql
```

Commands from this point run in the production EC2 SSH terminal rather than on the local machine.

## Connect to Amazon RDS for MySQL

From EC2, connect with the MySQL client:

```bash
mysql \
  -h <DB_ENDPOINT> \
  -P 3306 \
  -u <DB_USERNAME> \
  -p \
  <DB_NAME>
```

`<DB_ENDPOINT>`, `<DB_USERNAME>`, and `<DB_NAME>` are placeholders. The `-p` option prompts for the password interactively so it does not appear in the command or shell history.

After signing in, confirm the target database:

```sql
SELECT DATABASE();
SHOW TABLES;
```

Stop and correct the configuration if the selected database is not the intended production database.

## Initialize schema and data

In the MySQL client on EC2, run:

```sql
SOURCE /home/ec2-user/sql/001_schema.sql;
SOURCE /home/ec2-user/sql/002_admin_local.sql;
SOURCE /home/ec2-user/sql/003_services_seed.sql;
```

Each script has a specific role:

- `001_schema.sql` creates the database structure with `CREATE TABLE IF NOT EXISTS`.
- `002_admin_local.sql` creates the administrator inside a transaction and is used only for initial setup.
- `003_services_seed.sql` uses stable IDs and `ON DUPLICATE KEY UPDATE`, preventing duplicate service entries when rerun.

Before the administrator seed runs, confirm that the database does not already contain an administrator account, avoiding unique-constraint errors or unintended data.

## Validate the result

Use queries that do not expose sensitive data:

```sql
SHOW TABLES;

SELECT role, status, COUNT(*) AS total
FROM users
GROUP BY role, status;

SELECT id, name, price, is_active
FROM services
ORDER BY id;
```

Expected results:

- All seven main tables exist.
- An active administrator account exists.
- Five default services exist.
- No foreign-key, unique-constraint, or character-set error appears.

Do not query or capture phone numbers, email addresses, password hashes, or tokens. Exit the client with `EXIT;` after validation.

## Result

Amazon RDS for MySQL now contains the complete schema, initial administrator account, and service catalog. All initialization work was performed from EC2 without exposing the database directly to the Internet.
