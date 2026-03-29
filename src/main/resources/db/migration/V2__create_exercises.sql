-- V2: exercises reference table
CREATE TABLE exercises (
    id           VARCHAR(50)  PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    muscle_group VARCHAR(50),
    equipment    VARCHAR(50),
    icon         VARCHAR(10)
);
