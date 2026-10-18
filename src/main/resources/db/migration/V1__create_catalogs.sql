-- Catalogs (lookup tables) for the fields of the client's planner
-- (PLANIFICACION DE RECURSOS NPD, sheet PLANIFICADOR) that hold a closed or
-- repeated set of values. Names are unique case-insensitively so nobody can
-- create "QA Funcional" next to "QA FUNCIONAL".

CREATE FUNCTION set_updated_at() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- Fixed catalogs: short code + display name, seeded below

CREATE TABLE resource_status (
    id         smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       varchar(40)  NOT NULL UNIQUE,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE resource_status IS 'ESTADO: employment status only. Whether the employee has a project is derived in resource_v';

CREATE TABLE gender (
    id         smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       varchar(20)  NOT NULL UNIQUE,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE gender IS 'GENERO';

CREATE TABLE business_unit (
    id         smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       varchar(20)  NOT NULL UNIQUE,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE business_unit IS 'UEN';

CREATE TABLE equipment_provider (
    id         smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       varchar(20)  NOT NULL UNIQUE,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE equipment_provider IS 'EQUIPO / LAPTOP: who provides the laptop';

CREATE TABLE work_modality (
    id         smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       varchar(40)  NOT NULL UNIQUE,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE work_modality IS 'MODALIDAD LABORAL DEL RECURSO';

CREATE TABLE talent_review (
    id         smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       varchar(40)  NOT NULL UNIQUE,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE talent_review IS 'RESULTADO DE TALENT REVIEW. Restricted data: visibility depends on the role';

CREATE TABLE termination_reason (
    id         smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       varchar(40)  NOT NULL UNIQUE,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE termination_reason IS 'Desvinculación en Infosgroup: reason why the employee left or is leaving';

-- Open catalogs: values grow as they are registered

CREATE TABLE client (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        varchar(150) NOT NULL,
    is_internal boolean      NOT NULL DEFAULT false,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX client_name_uk ON client (lower(name));
COMMENT ON TABLE client IS 'CLIENTE: the company that hires Infosgroup. It stays when it has no projects';
COMMENT ON COLUMN client.is_internal IS 'Internal work such as NPD-ADMINISTRACION';

-- Projects are created on their own, before any employee is placed on them
CREATE TABLE project (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id  bigint       NOT NULL REFERENCES client (id),
    name       varchar(150) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX project_client_name_uk ON project (client_id, lower(name));
CREATE INDEX project_client_idx ON project (client_id);
COMMENT ON TABLE project IS 'PROYECTO ASIGNADO: a client''s project. "SIN PROYECTO" is not a project: it is an employee without project_id';

CREATE TABLE job_title (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       varchar(150) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX job_title_name_uk ON job_title (lower(name));
COMMENT ON TABLE job_title IS 'PUESTO: HR job title';

CREATE TABLE job_position (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       varchar(150) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX job_position_name_uk ON job_position (lower(name));
COMMENT ON TABLE job_position IS 'POSICIÓN EN INFOSGROUP: internal position including seniority (JR./SR.)';

CREATE TABLE client_role (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       varchar(150) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX client_role_name_uk ON client_role (lower(name));
COMMENT ON TABLE client_role IS 'ROL ACTUAL EN CLIENTE: the role the employee plays at the client (QA Funcional, Tester...)';

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['resource_status', 'gender', 'business_unit', 'equipment_provider',
                             'work_modality', 'talent_review', 'termination_reason', 'client',
                             'project', 'job_title', 'job_position', 'client_role']
    LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
                       t || '_set_updated_at', t);
    END LOOP;
END;
$$;

-- Seed of the fixed catalogs the application needs to work. Display names
-- are in Spanish because end users see them. No client or personal data from
-- the planner is loaded here.

INSERT INTO resource_status (code, name) VALUES
    ('ACTIVO',   'Activo'),
    ('LICENCIA', 'Licencia'),
    ('BAJA',     'Baja');

INSERT INTO gender (code, name) VALUES
    ('F', 'Femenino'),
    ('M', 'Masculino');

INSERT INTO business_unit (code, name) VALUES
    ('AE',  'AE'),
    ('IFP', 'IFP');

INSERT INTO equipment_provider (code, name) VALUES
    ('INFOS',   'Infos'),
    ('CLIENTE', 'Cliente');

-- Data-validation list of the planner's MODALIDAD LABORAL column, with its
-- typos fixed ('Pesencial', 'Hibrido . 1 al mes')
INSERT INTO work_modality (code, name) VALUES
    ('PRESENCIAL_L_V',    'Presencial - L/V'),
    ('PRESENCIAL_4_DIAS', 'Presencial - 4 Dias'),
    ('PRESENCIAL_L_S',    'Presencial - L/S'),
    ('HIBRIDO_1_DIA',     'Hibrido - 1 Dia'),
    ('HIBRIDO_2_DIAS',    'Hibrido - 2 Dias'),
    ('HIBRIDO_3_DIAS',    'Hibrido - 3 Dias'),
    ('HIBRIDO_1_AL_MES',  'Hibrido - 1 al mes'),
    ('HIBRIDO_2_AL_MES',  'Hibrido - 2 al mes'),
    ('VIRTUAL',           'Virtual');

-- "N/A" in the planner means no result and is stored as NULL
INSERT INTO talent_review (code, name) VALUES
    ('FUTURO_PROMETEDOR',  'Futuro prometedor'),
    ('FUTURO_LIDER',       'Futuro líder'),
    ('ESTRELLA_EMERGENTE', 'Estrella emergente'),
    ('DILEMA',             'Dilema');

INSERT INTO termination_reason (code, name) VALUES
    ('RENUNCIA_VOLUNTARIA',  'Renuncia voluntaria'),
    ('SALIDA_DEL_CLIENTE',   'Salida del cliente'),
    ('RECURSO_SIN_PROYECTO', 'Recurso sin proyecto');
