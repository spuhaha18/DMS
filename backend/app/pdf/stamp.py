import io
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from pypdf import PdfReader, PdfWriter


def append_signature_block(pdf_bytes: bytes, sigs: list[dict]) -> bytes:
    """Append a new last page containing the e-signature block.

    Each sig dict has: username, name, role, ts, meaning
    """
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    width, height = A4

    y = height - 60
    c.setFont("Helvetica-Bold", 14)
    c.drawString(50, y, "Electronic Signatures")
    y -= 30

    c.setFont("Helvetica", 10)
    for sig in sigs:
        c.drawString(50, y, f"{sig['role']}  |  {sig.get('name', sig['username'])} ({sig['username']})  |  {sig['ts']}")
        y -= 14
        c.drawString(50, y, f"  Meaning: {sig['meaning']}")
        y -= 20
        if y < 60:
            c.showPage()
            y = height - 60
            c.setFont("Helvetica", 10)

    c.save()

    stamp_reader = PdfReader(io.BytesIO(buf.getvalue()))
    base_reader = PdfReader(io.BytesIO(pdf_bytes))
    writer = PdfWriter()
    for page in base_reader.pages:
        writer.add_page(page)
    for page in stamp_reader.pages:
        writer.add_page(page)

    out = io.BytesIO()
    writer.write(out)
    return out.getvalue()
