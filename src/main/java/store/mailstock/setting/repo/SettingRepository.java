package store.mailstock.setting.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import store.mailstock.setting.entity.Setting;

@Repository
public interface SettingRepository extends JpaRepository<Setting, String> {}
