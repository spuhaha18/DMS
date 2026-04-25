import hashlib
from datetime import date
from sqlalchemy import String, Integer, Date, JSON, ForeignKey, Boolean
from sqlalchemy.orm import Mapped, mapped_column
from app.db import Base


class Project(Base):
    __tablename__ = "projects"
    id: Mapped[int] = mapped_column(primary_key=True)
    code: Mapped[str] = mapped_column(String(32), unique=True, nullable=False)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    owner_username: Mapped[str] = mapped_column(String(64), nullable=False)
    start_date: Mapped[date] = mapped_column(Date, nullable=False)
    end_date: Mapped[date] = mapped_column(Date, nullable=False)
    status: Mapped[str] = mapped_column(String(16), default="active", nullable=False)


class DocumentType(Base):
    __tablename__ = "document_types"
    id: Mapped[int] = mapped_column(primary_key=True)
    code: Mapped[str] = mapped_column(String(32), unique=True, nullable=False)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    numbering_pattern: Mapped[str] = mapped_column(String(128), nullable=False)
    default_approval_template_id: Mapped[int | None] = mapped_column(Integer, nullable=True)  # FK added later
    allowed_change_types: Mapped[list] = mapped_column(JSON, nullable=False)
    default_validity_months: Mapped[int | None] = mapped_column(Integer, nullable=True)
    template_version_policy: Mapped[str] = mapped_column(String(16), default="reject", nullable=False)


class Organization(Base):
    __tablename__ = "organizations"
    id: Mapped[int] = mapped_column(primary_key=True)
    code: Mapped[str] = mapped_column(String(32), unique=True, nullable=False)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    parent_id: Mapped[int | None] = mapped_column(ForeignKey("organizations.id"), nullable=True, index=True)


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    employee_no: Mapped[str | None] = mapped_column(String(32), nullable=True)
    email: Mapped[str | None] = mapped_column(String(200), nullable=True)
    org_id: Mapped[int | None] = mapped_column(ForeignKey("organizations.id"), nullable=True, index=True)
    title: Mapped[str | None] = mapped_column(String(64), nullable=True)
    role: Mapped[str] = mapped_column(String(32), default="Author", nullable=False)
    active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)


class Template(Base):
    __tablename__ = "templates"
    id: Mapped[int] = mapped_column(primary_key=True)
    doc_type_id: Mapped[int] = mapped_column(ForeignKey("document_types.id"), nullable=False, index=True)
    version: Mapped[str] = mapped_column(String(32), nullable=False)
    effective_date: Mapped[date] = mapped_column(Date, nullable=False)
    object_key: Mapped[str] = mapped_column(String(256), nullable=False)
    sha256: Mapped[str] = mapped_column(String(64), nullable=False)
