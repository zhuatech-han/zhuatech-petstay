// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;

/**
 * 持久化查询；实体类型由代码确定，条件值全部绑定。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信
 * zhuatech / zhuatech2
 */
@Repository
public class Store {
  @PersistenceContext EntityManager em;

  /** 清除本请求已读取的身份缓存，数据库事务行锁仍保持。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void clear() {
    em.clear();
  }

  /** 显式刷新受代理管理的持久化上下文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void flush() {
    em.flush();
  }

  /**
   * 查询指定实体；不存在返回 404。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
   * zhuatech2
   */
  public <T> T get(Class<T> type, Long id) {
    var v = em.find(type, id);
    if (v == null) throw new Problem(404, "NOT_FOUND");
    return v;
  }

  /**
   * 加行锁，序列化平台需求、报价、订单和配置的变更。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信
   * zhuatech / zhuatech2
   */
  public <T> T lock(Class<T> type, Long id) {
    var v = em.find(type, id, LockModeType.PESSIMISTIC_WRITE);
    if (v == null) throw new Problem(404, "NOT_FOUND");
    return v;
  }

  /**
   * 保存自有实体。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
   */
  public <T> T save(T entity) {
    em.persist(entity);
    return entity;
  }

  /**
   * 删除未被业务引用的实体，外键保护历史。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech
   * / zhuatech2
   */
  public void delete(Object entity) {
    em.remove(entity);
    em.flush();
  }

  /**
   * 查询有界列表，禁止客户端拼接 JPQL。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech
   * / zhuatech2
   */
  public <T> List<T> all(Class<T> type) {
    var rows =
        em.createQuery("from " + type.getSimpleName() + " e order by e.id", type)
            .setMaxResults(10001)
            .getResultList();
    if (rows.size() > 10000) throw new Problem(413, "RESOURCE_LIMIT");
    return rows;
  }

  /**
   * 执行固定查询，值参数由调用方绑定。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
   * zhuatech2
   */
  public <T> List<T> query(Class<T> type, String jpql, Object... params) {
    var q = em.createQuery(jpql, type);
    for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
    var rows = q.setMaxResults(10001).getResultList();
    if (rows.size() > 10000) throw new Problem(413, "RESOURCE_LIMIT");
    return rows;
  }

  /** 数据库计数，不加载实体或照片；查询由业务代码固定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public long count(String jpql, Object... params) {
    var q = em.createQuery(jpql, Long.class);
    for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
    return q.getSingleResult();
  }

  /** 有界分页，条件值绑定；查询由业务代码固定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public <T> List<T> page(Class<T> type, String jpql, int offset, int size, Object... params) {
    Rules.check(offset >= 0 && size >= 1 && size <= 50, "INVALID_INPUT");
    var q = em.createQuery(jpql, type);
    for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
    return q.setFirstResult(offset).setMaxResults(size).getResultList();
  }

  /** 在数据库统计照片实际字节，不加载全部BLOB。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public long photoBytes(Long orderId) {
    return ((Number)
            em.createNativeQuery(
                    "select coalesce(sum(octet_length(data)),0) from care_photo where order_id=?1")
                .setParameter(1, orderId)
                .getSingleResult())
        .longValue();
  }
}
