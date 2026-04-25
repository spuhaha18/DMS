import io
import pytest
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4


@pytest.fixture
def sample_pdf_bytes():
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    c.drawString(100, 700, "Test Document")
    c.save()
    return buf.getvalue()


def test_signature_block_appended(sample_pdf_bytes):
    from app.pdf.stamp import append_signature_block
    from pypdf import PdfReader

    sigs = [
        {"username": "alice", "name": "Alice Kim", "role": "Author", "ts": "2026-04-26 10:00", "meaning": "작성"},
        {"username": "bob", "name": "Bob Lee", "role": "Reviewer", "ts": "2026-04-26 11:00", "meaning": "검토 동의"},
        {"username": "dave", "name": "Dave Park", "role": "Approver", "ts": "2026-04-26 12:00", "meaning": "최종 승인"},
    ]
    result = append_signature_block(sample_pdf_bytes, sigs)

    assert result[:4] == b"%PDF"
    reader = PdfReader(io.BytesIO(result))
    # Original had 1 page; now should have 2 (original + signature page)
    assert len(reader.pages) == 2
