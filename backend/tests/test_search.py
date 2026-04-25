from datetime import date
import pytest


def _auth_header(username="alice", role="Author"):
    from app.auth.session import issue
    token = issue(username, role)
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def seeded_docs(test_client):
    """Create a few documents for search tests, using the same session as test_client."""
    from app.models.master import Project, DocumentType, Template
    from app.models.document import Document

    session = test_client._test_session

    p = Project(code="SRCH-P", name="Search Project", owner_username="alice",
                start_date=date(2026, 1, 1), end_date=date(2027, 12, 31))
    session.add(p)
    session.flush()

    dt = DocumentType(code="SOP-SR", name="SOP Search",
                      numbering_pattern="{project_code}-SOP-{seq:04d}",
                      allowed_change_types=["New"],
                      template_version_policy="reject")
    session.add(dt)
    session.flush()

    t = Template(doc_type_id=dt.id, version="1.0",
                 effective_date=date(2026, 1, 1),
                 object_key="templates/sr.docx", sha256="abc")
    session.add(t)
    session.flush()

    docs = []
    for i, title in enumerate(["세포배양 SOP", "프로토콜 A", "세포배양 실험"], start=1):
        status = "Effective" if i == 1 else "Draft"
        doc = Document(
            doc_number=f"SRCH-P-SOP-{i:04d}", revision="A",
            document_type_id=dt.id, project_id=p.id,
            author_username="alice", title=title,
            effective_status=status,
            template_id=t.id, template_version="1.0",
            source_docx_key=f"sources/SRCH-P-SOP-{i:04d}.docx",
        )
        session.add(doc)
        docs.append(doc)
    session.flush()
    return docs


def test_search_by_doc_number(test_client, seeded_docs):
    r = test_client.get("/documents/search?q=SRCH-P-SOP-0001", headers=_auth_header())
    assert r.status_code == 200, r.json()
    results = r.json()["results"]
    assert len(results) >= 1
    assert any(d["doc_number"] == "SRCH-P-SOP-0001" for d in results)


def test_search_by_korean_title(test_client, seeded_docs):
    r = test_client.get("/documents/search?q=세포배양", headers=_auth_header())
    assert r.status_code == 200, r.json()
    results = r.json()["results"]
    assert any("세포배양" in d["title"] for d in results)


def test_search_filters_by_effective_only(test_client, seeded_docs):
    r = test_client.get("/documents/search?q=SOP&effective_only=true", headers=_auth_header())
    assert r.status_code == 200, r.json()
    results = r.json()["results"]
    assert len(results) >= 1
    assert all(d["effective_status"] == "Effective" for d in results)
