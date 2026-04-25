from sqlalchemy import Integer, String, ForeignKey, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column
from app.db import Base


class NumberingSequence(Base):
    __tablename__ = "numbering_sequences"
    id: Mapped[int] = mapped_column(primary_key=True)
    project_id: Mapped[int] = mapped_column(ForeignKey("projects.id"), nullable=False, index=True)
    doc_type_id: Mapped[int] = mapped_column(ForeignKey("document_types.id"), nullable=False, index=True)
    period: Mapped[str] = mapped_column(String(16), nullable=False)  # "all" | "2026" | "2026-04"
    next_seq: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    __table_args__ = (UniqueConstraint("project_id", "doc_type_id", "period"),)
