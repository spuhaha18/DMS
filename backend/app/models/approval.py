from datetime import datetime
from sqlalchemy import String, Integer, ForeignKey, DateTime, Text, JSON
from sqlalchemy.orm import Mapped, mapped_column
from app.db import Base


class ApprovalTemplate(Base):
    __tablename__ = "approval_templates"
    id: Mapped[int] = mapped_column(primary_key=True)
    doc_type_id: Mapped[int] = mapped_column(ForeignKey("document_types.id"), nullable=False, index=True)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    # {"reviewers": ["bob", "carol"], "approvers": ["dave"]}
    config: Mapped[dict] = mapped_column(JSON, nullable=False)


class Approval(Base):
    __tablename__ = "approvals"
    id: Mapped[int] = mapped_column(primary_key=True)
    document_id: Mapped[int] = mapped_column(ForeignKey("documents.id"), nullable=False, index=True)
    step_order: Mapped[int] = mapped_column(Integer, nullable=False)  # 1=Author, 2=Review, 3=Approve
    role: Mapped[str] = mapped_column(String(16), nullable=False)      # Author/Reviewer/Approver
    assigned_username: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    status: Mapped[str] = mapped_column(String(16), default="Pending", nullable=False)
    decided_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    comment: Mapped[str | None] = mapped_column(Text, nullable=True)
    signature_meaning: Mapped[str | None] = mapped_column(String(64), nullable=True)
