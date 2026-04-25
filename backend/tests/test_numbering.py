import pytest
from datetime import datetime, date
from app.numbering.service import assign_number


@pytest.fixture
def project(db):
    from app.models.master import Project
    p = Project(code="NUM-P", name="Numbering Test", owner_username="alice",
                start_date=date(2026, 1, 1), end_date=date(2027, 12, 31))
    db.add(p)
    db.flush()
    return p


@pytest.fixture
def sop_doctype(db):
    from app.models.master import DocumentType
    dt = DocumentType(code="SOP-N", name="SOP",
                      numbering_pattern="{project_code}-SOP-{seq:04d}",
                      allowed_change_types=["New", "Revision"],
                      template_version_policy="reject")
    db.add(dt)
    db.flush()
    return dt


def test_assign_number_sequential(db, project, sop_doctype):
    pj_id = project.id
    dt_id = sop_doctype.id
    n1 = assign_number(db, pj_id, dt_id)
    n2 = assign_number(db, pj_id, dt_id)
    db.flush()
    assert n1 == "NUM-P-SOP-0001"
    assert n2 == "NUM-P-SOP-0002"


def test_assign_number_year_period(db, project, sop_doctype):
    """Test numbering pattern with {year} resets per year."""
    from app.models.master import DocumentType
    pj_id = project.id
    # Add a doctype with year-based pattern
    dt_year = DocumentType(code="RPT-N", name="Report",
                           numbering_pattern="{project_code}-{year}-RPT-{seq:04d}",
                           allowed_change_types=["New"],
                           template_version_policy="reject")
    db.add(dt_year)
    db.flush()

    n1 = assign_number(db, pj_id, dt_year.id, now=datetime(2026, 3, 1))
    n2 = assign_number(db, pj_id, dt_year.id, now=datetime(2026, 6, 1))
    n3 = assign_number(db, pj_id, dt_year.id, now=datetime(2027, 1, 1))
    assert n1 == "NUM-P-2026-RPT-0001"
    assert n2 == "NUM-P-2026-RPT-0002"  # same year, continues
    assert n3 == "NUM-P-2027-RPT-0001"  # new year, resets


def test_assign_number_different_doctypes_independent(db, project, sop_doctype):
    """Different doc types have independent sequences."""
    from app.models.master import DocumentType
    pj_id = project.id
    dt2 = DocumentType(code="PROTO-N", name="Protocol",
                       numbering_pattern="{project_code}-PROTO-{seq:04d}",
                       allowed_change_types=["New"],
                       template_version_policy="reject")
    db.add(dt2)
    db.flush()

    n_sop = assign_number(db, pj_id, sop_doctype.id)
    n_proto = assign_number(db, pj_id, dt2.id)
    assert n_sop.endswith("-0001")
    assert n_proto.endswith("-0001")
    assert n_sop != n_proto


def test_assign_number_monthly_period(db, project):
    """Monthly {ym} pattern resets each month."""
    from app.models.master import DocumentType
    dt = DocumentType(code="MONTHLY-N", name="Monthly",
                      numbering_pattern="{project_code}-{ym}-{seq:04d}",
                      allowed_change_types=["New"],
                      template_version_policy="reject")
    db.add(dt); db.flush()
    pj_id = project.id

    n1 = assign_number(db, pj_id, dt.id, now=datetime(2026, 3, 1))
    n2 = assign_number(db, pj_id, dt.id, now=datetime(2026, 3, 15))
    n3 = assign_number(db, pj_id, dt.id, now=datetime(2026, 4, 1))
    assert n1 == "NUM-P-2026-03-0001"
    assert n2 == "NUM-P-2026-03-0002"  # same month, continues
    assert n3 == "NUM-P-2026-04-0001"  # new month, resets


def test_assign_number_missing_project_raises(db, sop_doctype):
    """Missing project raises ValueError."""
    with pytest.raises(ValueError, match="not found"):
        assign_number(db, project_id=99999, doc_type_id=sop_doctype.id)
