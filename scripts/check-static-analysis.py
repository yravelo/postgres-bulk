#!/usr/bin/env python3
"""Validate the fail-closed SpotBugs/FindSecBugs reports and narrow exclusions."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
PARENT = REPOSITORY_ROOT / "code" / "postgres-bulk-parent"
PRODUCTIVE_MODULES = (
    "postgres-bulk-core",
    "postgres-bulk-pgjdbc",
    "postgres-bulk-hibernate",
    "postgres-bulk-spring-data",
    "postgres-bulk-spring-data-jdbc",
    "postgres-bulk-spring-boot-autoconfigure",
    "postgres-bulk-spring-boot-autoconfigure-jdbc",
)
EXCLUDED_MODULES = (
    "postgres-bulk-spring-boot-starter",
    "postgres-bulk-spring-boot-starter-data-jdbc",
    "postgres-bulk-benchmarks",
)
EXPECTED_EXCLUSIONS = {
    (
        "EI_EXPOSE_REP",
        "io.ybr.postgresbulk.core.metadata.BulkKeyMetadata",
        "components",
    ),
    (
        "EI_EXPOSE_REP",
        "io.ybr.postgresbulk.core.metadata.EntityMetadata",
        "insertColumns",
    ),
    (
        "SQL_INJECTION_JDBC",
        "io.ybr.postgresbulk.pgjdbc.copy.TemporaryTableBulkLookup",
        "executeStatement",
    ),
    (
        "SQL_INJECTION_JDBC",
        "io.ybr.postgresbulk.pgjdbc.copy.TemporaryTableBulkLookup",
        "cleanup",
    ),
    (
        "SQL_INJECTION_JDBC",
        "io.ybr.postgresbulk.springdata.jdbc.DefaultSpringDataJdbcBulkOperations$EntityRowMapperMaterializer",
        "materialize",
    ),
    (
        "CT_CONSTRUCTOR_THROW",
        "io.ybr.postgresbulk.springdata.repository.DefaultPostgresBulkOperations",
        "<init>",
    ),
}
REVIEW_DATE = re.compile(r"owner:\s*yravelo;\s*review-by:\s*(\d{4}-\d{2}-\d{2})")


def fail(message: str) -> None:
    print(f"Static-analysis audit failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def audit_exclusions() -> None:
    path = REPOSITORY_ROOT / "config" / "security" / "spotbugs-exclude.xml"
    text = path.read_text(encoding="utf-8")
    review_dates = [date.fromisoformat(value) for value in REVIEW_DATE.findall(text)]
    if len(review_dates) != len(EXPECTED_EXCLUSIONS):
        fail("every exact exclusion must have one owner and review-by comment")
    expired = [value.isoformat() for value in review_dates if value < date.today()]
    if expired:
        fail(f"expired exclusion review dates: {', '.join(expired)}")

    root = ET.fromstring(text)
    actual: set[tuple[str, str, str]] = set()
    matches = root.findall("Match")
    for match in matches:
        if len(match) != 3:
            fail("each exclusion must contain exactly Bug, Class and Method matchers")
        bug = match.find("Bug")
        class_match = match.find("Class")
        method = match.find("Method")
        if bug is None or class_match is None or method is None:
            fail("package/category-wide exclusions are forbidden")
        if set(bug.attrib) != {"pattern"} or set(class_match.attrib) != {"name"}:
            fail("bug and class exclusions must use exact pattern/name attributes")
        if set(method.attrib) != {"name"}:
            fail("method exclusions must use an exact method name")
        actual.add((bug.attrib["pattern"], class_match.attrib["name"], method.attrib["name"]))

    if len(matches) != len(actual):
        fail("duplicate exclusions are forbidden")
    if actual != EXPECTED_EXCLUSIONS:
        fail("the exclusion set changed without updating the reviewed static-analysis policy")

    for _, class_name, method_name in actual:
        top_level_class = class_name.split("$", 1)[0]
        relative_source = Path(*top_level_class.split(".")).with_suffix(".java")
        candidates = list(PARENT.glob(f"*/src/main/java/{relative_source}"))
        if len(candidates) != 1:
            fail(f"stale exclusion class target: {class_name}")
        source = candidates[0].read_text(encoding="utf-8")
        expected_method = top_level_class.rsplit(".", 1)[-1] if method_name == "<init>" else method_name
        if re.search(rf"\b{re.escape(expected_method)}\s*\(", source) is None:
            fail(f"stale exclusion method target: {class_name}.{method_name}")


def audit_report(module: str) -> None:
    report = PARENT / module / "target" / "spotbugsXml.xml"
    if not report.is_file():
        fail(f"missing report for productive module {module}")
    root = ET.parse(report).getroot()
    plugin = root.find("./Project/Plugin[@id='com.h3xstream.findsecbugs']")
    if plugin is None or plugin.attrib.get("enabled") != "true":
        fail(f"FindSecBugs is not enabled in {module}")
    errors = root.find("Errors")
    if errors is None:
        fail(f"missing analyzer error summary in {module}")
    if errors.attrib.get("errors") != "0" or errors.attrib.get("missingClasses") != "0":
        fail(f"analyzer errors or missing classes in {module}")
    summary = root.find("FindBugsSummary")
    if summary is None or summary.attrib.get("total_bugs") != "0":
        count = "unknown" if summary is None else summary.attrib.get("total_bugs", "unknown")
        fail(f"{module} contains {count} untriaged findings")


def main() -> None:
    audit_exclusions()
    for module in PRODUCTIVE_MODULES:
        audit_report(module)
    for module in EXCLUDED_MODULES:
        report = PARENT / module / "target" / "spotbugsXml.xml"
        if report.exists():
            fail(f"excluded module unexpectedly produced a report: {module}")
    for example in ("spring-boot-basic", "spring-boot-data-jdbc"):
        report = REPOSITORY_ROOT / "examples" / example / "target" / "spotbugsXml.xml"
        if report.exists():
            fail(f"example unexpectedly produced a report: {example}")
    print(
        "Static-analysis report audit: PASS "
        f"({len(PRODUCTIVE_MODULES)} modules, FindSecBugs enabled, 0 findings)"
    )


if __name__ == "__main__":
    main()
