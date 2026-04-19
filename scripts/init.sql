-- Exclusion Constraint에서 정수(location_id =)와 범위(tsrange &&)를 GiST 인덱스로 함께 사용하기 위해 필요
CREATE EXTENSION IF NOT EXISTS btree_gist;
