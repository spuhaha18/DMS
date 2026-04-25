import pytest
from alembic.config import Config
from alembic import command
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from fastapi.testclient import TestClient
from app.main import app
from app.deps import get_db
from app.config import settings


@pytest.fixture(scope="session")
def engine():
    e = create_engine(settings.DATABASE_URL)
    alembic_cfg = Config("alembic.ini")
    command.upgrade(alembic_cfg, "head")
    yield e
    e.dispose()


@pytest.fixture
def db(engine):
    SessionFactory = sessionmaker(bind=engine)
    session = SessionFactory()
    yield session
    session.rollback()
    session.close()


@pytest.fixture
def test_client(engine):
    """TestClient with get_db overridden to use a rolled-back transaction.

    Uses SQLAlchemy 2.0-compatible SAVEPOINT nesting so that session.commit()
    inside the API handlers only releases the savepoint, while the outer
    connection-level transaction is rolled back after the test.

    When used together with the `db` fixture in the same test, both share the
    same underlying connection so data inserted via `db` is visible to API calls.
    """
    conn = engine.connect()
    trans = conn.begin()

    # join_transaction_mode="create_savepoint" makes session.commit() issue
    # RELEASE SAVEPOINT instead of COMMIT, keeping the outer transaction alive.
    SessionFactory = sessionmaker(bind=conn, join_transaction_mode="create_savepoint")
    session = SessionFactory()

    def override_get_db():
        try:
            yield session
        finally:
            pass  # lifecycle managed here

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as client:
        # Expose the session so co-fixtures (like `db`) can bind to it
        client._test_session = session
        yield client
    app.dependency_overrides.clear()
    session.close()
    trans.rollback()
    conn.close()
