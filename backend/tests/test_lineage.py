from datetime import date
import pytest


def _auth_header(username="alice", role="Author"):
    from app.auth.session import issue
    token = issue(username, role)
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def doc_chain(test_client):
    """Create a 3-doc chain: RevA (Superseded) → RevB (Effective, revision of RevA) → Amendment (Effective).

    Uses test_client._test_session so data is visible to API calls within the
    same rolled-back transaction.
    """
    from app.models.master import Project, DocumentType, Template
    from app.models.document import Document

    session = test_client._test_session

    p = Project(
        code="LIN-P", name="Lineage Project", owner_username="alice",
        start_date=date(2026, 1, 1), end_date=date(2027, 12, 31),
    )
    session.add(p)
    session.flush()

    dt = DocumentType(
        code="SOP-LIN", name="SOP Lineage",
        numbering_pattern="{project_code}-SOP-{seq:04d}",
        allowed_change_types=["New", "Revision", "Amendment"],
        template_version_policy="reject",
    )
    session.add(dt)
    session.flush()

    t = Template(
        doc_type_id=dt.id, version="1.0",
        effective_date=date(2026, 1, 1),
        object_key="templates/lin.docx", sha256="abc",
    )
    session.add(t)
    session.flush()

    # RevA — Superseded (unique doc_number with revision suffix so unique constraint is satisfied)
    doc_a = Document(
        doc_number="LIN-P-SOP-0001-A", revision="A",
        document_type_id=dt.id, project_id=p.id,
        author_username="alice", title="Original SOP",
        effective_status="Superseded",
        effective_date=date(2026, 1, 1),
        template_id=t.id, template_version="1.0",
        source_docx_key="sources/LIN-P-SOP-0001-A.docx",
    )
    session.add(doc_a)
    session.flush()

    # RevB — Effective, revision of RevA (canonical doc_number without suffix)
    doc_b = Document(
        doc_number="LIN-P-SOP-0001", revision="B",
        document_type_id=dt.id, project_id=p.id,
        author_username="alice", title="Revised SOP",
        effective_status="Effective",
        effective_date=date(2026, 3, 1),
        parent_document_id=doc_a.id,
        relation_type="revision",
        template_id=t.id, template_version="1.0",
        source_docx_key="sources/LIN-P-SOP-0001-B.docx",
    )
    session.add(doc_b)
    session.flush()

    # Amendment — child of RevB
    doc_am = Document(
        doc_number="LIN-P-SOP-0001-AM260420", revision="B",
        document_type_id=dt.id, project_id=p.id,
        author_username="alice", title="Amendment to SOP",
        effective_status="Effective",
        effective_date=date(2026, 4, 20),
        parent_document_id=doc_b.id,
        relation_type="amendment",
        template_id=t.id, template_version="1.0",
        source_docx_key="sources/LIN-P-SOP-0001-AM260420.docx",
    )
    session.add(doc_am)
    session.flush()

    return [doc_a, doc_b, doc_am]


def test_lineage_returns_full_chain(test_client, doc_chain):
    doc_a = doc_chain[0]
    r = test_client.get(f"/documents/{doc_a.id}/lineage", headers=_auth_header())
    assert r.status_code == 200, r.json()
    chain = r.json()["chain"]
    # doc_a is root, has 1 child (doc_b), doc_b has 1 child (doc_am)
    # lineage of doc_a: [doc_a, doc_b] (only direct children)
    assert len(chain) >= 2
    assert chain[0]["id"] == doc_a.id


def test_lineage_of_middle_node(test_client, doc_chain):
    doc_a, doc_b, doc_am = doc_chain
    r = test_client.get(f"/documents/{doc_b.id}/lineage", headers=_auth_header())
    assert r.status_code == 200, r.json()
    chain = r.json()["chain"]
    # doc_b: parent=doc_a, children=[doc_am]
    assert len(chain) == 3
    assert chain[0]["id"] == doc_a.id
    assert chain[1]["id"] == doc_b.id
    assert chain[2]["id"] == doc_am.id


def test_get_by_doc_number_latest_effective(test_client, doc_chain):
    # doc_b has doc_number="LIN-P-SOP-0001" and is Effective
    r = test_client.get(
        "/documents/by-number?doc_number=LIN-P-SOP-0001&latest_effective=true",
        headers=_auth_header(),
    )
    assert r.status_code == 200, r.json()
    body = r.json()
    assert body["effective_status"] == "Effective"
    assert body["revision"] == "B"


def test_get_by_doc_number_not_found(test_client, doc_chain):
    r = test_client.get(
        "/documents/by-number?doc_number=DOES-NOT-EXIST",
        headers=_auth_header(),
    )
    assert r.status_code == 404


def test_lineage_not_found(test_client):
    r = test_client.get("/documents/999999/lineage", headers=_auth_header())
    assert r.status_code == 404
