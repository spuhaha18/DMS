import subprocess
import tempfile
import pathlib


def docx_to_pdf(docx_bytes: bytes, *, timeout_sec: int = 60) -> bytes:
    with tempfile.TemporaryDirectory() as d:
        src = pathlib.Path(d) / "in.docx"
        src.write_bytes(docx_bytes)
        subprocess.run(
            ["libreoffice", "--headless", "--convert-to", "pdf", "--outdir", d, str(src)],
            check=True,
            timeout=timeout_sec,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
        return (pathlib.Path(d) / "in.pdf").read_bytes()
