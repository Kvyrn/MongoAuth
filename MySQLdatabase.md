## Globals table

| Name      | Data type                           |
|-----------|-------------------------------------|
| Password  | TINYTEXT                            |
| Timestamp | TIMESTAMP DEFAULT CURRENT_TIMESTAMP |

## Inventory table

| Name    | Data type                  |
|---------|----------------------------|
| Id      | BIGINT                     |
| Server  | TINYTEXT                   |
| Uuid    | CHAR(36)                   |
| Slot    | INT                        |
| Section | ENUM(main, armor, offhand) |
| Value   | MEDIUMTEXT                 |

## InventoryOwners table

| Name      | Data type |
|-----------|-----------|
| Uuid      | CHAR(36)  |
| Server    | TINYTEXT  |
| LastInvId | BIGINT    |

## ServerPlayers table

| Name   | Data type |
|--------|-----------|
| Uuid   | CHAR(36)  |
| Server | TINYTEXT  |
| XPos   | DOUBLE    |
| YPos   | DOUBLE    |
| ZPos   | DOUBLE    |

## Players Table

| Name                | Data type |
|---------------------|-----------|
| Uuid (key)          | CHAR(36)  |
| Password            | TEXT      |
| LeftUnauthenticated | BOOL      |
| SessionIP           | TINYTEXT  |
| ExpiresOn           | BIGINT    |
