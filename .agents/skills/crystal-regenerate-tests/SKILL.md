---
name: crystal-regenerate-tests
description: Regenerate parser test golden files after BNF changes. Use when parser tests fail due to changed grammar like new tokens or modified rules. Triggers on regenerate golden files, parser test fails, PsiErrorElement expected, update golden files.
---

# Crystal Parser Test Regeneration

When BNF changes cause parser test golden files to mismatch:

## Workflow

### 1. Identify failing tests
```bash
./gradlew test 2>&1 | grep FAILED
```

### 2. Delete affected golden files
```bash
rm -f src/test/testData/parser/TestName.txt
```
For multiple tests:
```bash
rm -f src/test/testData/parser/TestOne.txt src/test/testData/parser/TestTwo.txt
```

### 3. Regenerate by running the specific test
```bash
./gradlew test --tests "de.magynhard.crystal.parser.CrystalParserTest.testName"
```
First run generates the `.txt` file (and fails — expected). Re-check for errors:
```bash
rg "PsiErrorElement" src/test/testData/parser/TestName.txt && echo "HAS ERRORS" || echo "NO ERRORS"
```

### 4. Verify no parse errors in regenerated files
```bash
rg "PsiErrorElement" src/test/testData/parser/TestName.txt | wc -l
```
Must return `0`. If errors exist, the BNF change broke parsing — fix the grammar, not the golden file.

### 5. Confirm all tests pass
```bash
./gradlew test
```

## Common Causes of Golden File Mismatches
- New token type added to BNF tokens list (changes serialization order)
- New alternative added to `primary_expression` or `bare_primary_expression` (affects PSI tree shape)
- Rule modified to accept new tokens (different PSI element nesting)
- Lexer change that alters token boundaries
