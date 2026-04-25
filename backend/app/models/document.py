from datetime import date, datetime
from sqlalchemy import String, Integer, Date, ForeignKey, DateTime
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.dialects.postgresql import TSVECTOR
from app.db import Base


class Document(Base):
    __tablename__ = "documents"
    id: Mapped[int] = mapped_column(primary_key=True)
    doc_number: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    revision: Mapped[str | None] = mapped_column(String(8), nullable=True)
    document_type_id: Mapped[int] = mapped_column(ForeignKey("document_types.id"), nullable=False, index=True)
    project_id: Mapped[int] = mapped_column(ForeignKey("projects.id"), nullable=False, index=True)
    author_username: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    parent_document_id: Mapped[int | None] = mapped_column(ForeignKey("documents.id"), nullable=True)
    relation_type: Mapped[str | None] = mapped_column(String(16), nullable=True)
    title: Mapped[str] = mapped_column(String(500), nullable=False)
    effective_status: Mapped[str] = mapped_column(String(16), default="Draft", nullable=False)
    effective_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    expiration_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    security_level: Mapped[str] = mapped_column(String(16), default="Confidential", nullable=False)
    template_id: Mapped[int] = mapped_column(ForeignKey("templates.id"), nullable=False)
    template_version: Mapped[str] = mapped_column(String(32), nullable=False)
    source_docx_key: Mapped[str] = mapped_column(String(256), nullable=False)
    final_pdf_object_key: Mapped[str | None] = mapped_column(String(256), nullable=True)
    final_pdf_sha256: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default="now()", nullable=False)
    search_tsv: Mapped[str | None] = mapped_column(TSVECTOR, nullable=True)
