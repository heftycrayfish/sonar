HOW TO ADD A MIGRATION

* Jump some versions when adding the first Ruby on Rails migration of a new sonar version. For example if sonar 2.10 is 193, then sonar 2.11 should start at 200.
* Complete the DDL files for Derby :
  + sonar-core/src/main/resources/org/sonar/persistence/schema-derby.ddl
  + complete sonar-core/src/main/resources/org/sonar/persistence/rows-derby.ddl :
    - add "INSERT INTO SCHEMA_MIGRATIONS(VERSION) VALUES ('<THE MIGRATION ID>')"
* Update the migration id defined in sonar-core/src/main/java/org/sonar/jpa/entity/SchemaMigration.java
* To be removed soon : complete sonar-testing-harness/src/main/resources/org/sonar/test/persistence/sonar-test.ddl




RECOMMANDATIONS

* Don't forget that index name limited to 30 characters in Oracle DB.
* Prefer to add nullable columns to avoid problems during migration.