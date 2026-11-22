-- 근거를 주요(본 위치의 코드·지시문·설정)와 참고(테스트·예제·문서 폴더)로 나눈다. 참고도 지우지 않고 뒤에 보여 준다
alter table finding add column is_primary boolean not null default true;
