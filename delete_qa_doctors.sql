-- Safety check: find the QA doctors to delete
SELECT u.id, u.email, u.full_name, u.role, d.id AS doctor_id
FROM users u
LEFT JOIN doctor d ON d.user_id = u.id
WHERE u.full_name ILIKE '%qa test%' OR u.full_name ILIKE '%qa two updated%';
