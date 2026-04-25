import asyncio
from email.message import EmailMessage
from app.config import settings


async def _send_async(to: str, subject: str, body: str) -> None:
    import aiosmtplib
    msg = EmailMessage()
    msg["From"] = settings.SMTP_FROM
    msg["To"] = to
    msg["Subject"] = subject
    msg.set_content(body)
    await aiosmtplib.send(
        msg,
        hostname=settings.SMTP_HOST,
        port=settings.SMTP_PORT,
        username=settings.SMTP_USER or None,
        password=settings.SMTP_PASSWORD or None,
        use_tls=settings.SMTP_TLS,
    )


def send_notification(to: str, subject: str, body: str) -> None:
    """Fire-and-forget sync wrapper. Silently ignores send failures (best-effort)."""
    try:
        asyncio.get_event_loop().run_until_complete(_send_async(to, subject, body))
    except Exception:
        pass  # notifications are best-effort


async def notify_assignment(*, to: str, doc_number: str, role: str) -> None:
    """Notify a user they have a pending approval."""
    await _send_async(
        to=to,
        subject=f"[DMS] {role} 결재 요청: {doc_number}",
        body=f"{doc_number} 문서가 {role} 단계에 도착했습니다.\n\nhttps://dms/inbox",
    )


async def notify_decision(*, to: str, doc_number: str, role: str, decision: str) -> None:
    """Notify the document author of a review/approval decision."""
    await _send_async(
        to=to,
        subject=f"[DMS] {doc_number} {role} {decision}",
        body=f"{doc_number} 문서가 {role}에서 {decision} 처리되었습니다.",
    )
