import pytest
from alembic.config import Config
from alembic import command
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.db import Base
from app.config import settings


@pytest.fixture(scope="session")
def engine():
    e = create_engine(settings.DATABASE_URL)
    # Apply migrations (including REVOKE) so DB-level enforcement is in place
    alembic_cfg = Config("alembic.ini")
    command.upgrade(alembic_cfg, "head")
    yield e
    e.dispose()


@pytest.fixture
def db(engine):
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.rollback()
    session.close()
