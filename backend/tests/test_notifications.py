import pytest
from unittest.mock import AsyncMock, patch


@pytest.mark.asyncio
async def test_notify_assignment_sends_correct_email():
    with patch("aiosmtplib.send", new_callable=AsyncMock) as mock_send:
        from app.notifications.smtp import notify_assignment
        await notify_assignment(to="bob@example.com", doc_number="PROJ-SOP-0001", role="Reviewer")
        mock_send.assert_called_once()
        msg = mock_send.call_args[0][0]
        assert msg["To"] == "bob@example.com"
        assert "PROJ-SOP-0001" in msg["Subject"]
        assert "Reviewer" in msg["Subject"] or "Reviewer" in msg.get_content()


@pytest.mark.asyncio
async def test_notify_decision_sends_correct_email():
    with patch("aiosmtplib.send", new_callable=AsyncMock) as mock_send:
        from app.notifications.smtp import notify_decision
        await notify_decision(to="alice@example.com", doc_number="PROJ-SOP-0001", role="Reviewer", decision="Approved")
        mock_send.assert_called_once()
        msg = mock_send.call_args[0][0]
        assert msg["To"] == "alice@example.com"
        assert "PROJ-SOP-0001" in msg["Subject"]
