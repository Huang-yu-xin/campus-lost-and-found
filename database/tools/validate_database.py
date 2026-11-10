"""以本机 MySQL 二进制在独立临时目录验证 DDL，不连接既有服务。

仅使用 Python 标准库。临时数据库绑定 127.0.0.1 随机端口，无真实数据，
初始化为空密码 root，验证结束关闭自己启动的进程，不删除任何既有目录。
日志和数据目录保留在系统 TEMP，便于复核。运行失败返回非零退出码。
"""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse
import json
import os
import re
import socket
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT.parent/'docs'
CHECKS=[]

def check(name, condition, detail=''):
    if not condition:
        raise AssertionError(name+': '+detail)
    CHECKS.append({'name':name,'status':'passed','detail':detail})
    print('PASS '+name,flush=True)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--mysql-home',default='D:/MySQL/mysql-8.0.34-winx64')
    args=parser.parse_args()
    mysql_home=Path(args.mysql_home).resolve()
    mysql=mysql_home/'bin/mysql.exe'
    mysqld=mysql_home/'bin/mysqld.exe'
    mysqladmin=mysql_home/'bin/mysqladmin.exe'
    for p in (mysql,mysqld,mysqladmin):
        if not p.is_file():raise FileNotFoundError(p)
    work=Path(tempfile.mkdtemp(prefix='campus-db-validation-'))
    data=work/'data'
    log=work/'mysqld.log'
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0))
        port=sock.getsockname()[1]
    flags=getattr(subprocess,'CREATE_NO_WINDOW',0)
    print('隔离验证目录：'+str(work),flush=True)
    initialized=subprocess.run([str(mysqld),'--no-defaults','--initialize-insecure',
        '--basedir='+str(mysql_home),'--datadir='+str(data),'--log-error='+str(log)],
        capture_output=True,encoding='utf-8',errors='replace',creationflags=flags,timeout=90)
    if initialized.returncode:
        raise RuntimeError('初始化失败：'+initialized.stderr+'\n'+(log.read_text(encoding='utf-8',errors='replace') if log.exists() else ''))
    proc=subprocess.Popen([str(mysqld),'--no-defaults','--basedir='+str(mysql_home),
        '--datadir='+str(data),'--bind-address=127.0.0.1','--port='+str(port),
        '--mysqlx=OFF','--skip-log-bin','--performance-schema=OFF',
        '--innodb-buffer-pool-size=67108864','--secure-file-priv=NULL','--log-error='+str(log)],
        stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=flags)
    base=[str(mysql),'--no-defaults','--protocol=TCP','--host=127.0.0.1','--port='+str(port),'--user=root',
          '--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--connect-timeout=2']
    def run(sql, db=True, expected_error=None):
        command=base+(['campus_lost_found'] if db else [])
        result=subprocess.run(command,input="SET time_zone='+00:00';\n"+sql,encoding='utf-8',errors='replace',capture_output=True,creationflags=flags,timeout=30)
        if expected_error is not None:
            m=re.search(r'ERROR (\d+)',result.stderr)
            codes={expected_error} if isinstance(expected_error,int) else set(expected_error)
            if result.returncode==0 or not m or int(m.group(1)) not in codes:
                raise AssertionError('未出现预期数据库错误 '+str(codes)+': '+result.stdout+result.stderr)
            return result.stderr.strip()
        if result.returncode:
            raise RuntimeError(result.stderr+'\nSQL: '+sql[:1600])
        return result.stdout.strip()
    report={'status':'failed','mysqlVersion':None,'isolated':True,'existingServiceModified':False,'temporaryDirectory':str(work),'checks':CHECKS}
    try:
        for _ in range(60):
            if proc.poll() is not None:raise RuntimeError('隔离服务启动失败：'+log.read_text(encoding='utf-8',errors='replace'))
            try:
                version=run('SELECT VERSION();',db=False)
                break
            except RuntimeError:time.sleep(0.3)
        else:raise RuntimeError('隔离服务启动超时')
        report['mysqlVersion']=version
        check('独立 MySQL 实例可连接',version.startswith('8.0.34'),version)
        run((ROOT/'000_create_database.sql').read_text(encoding='utf-8'),db=False)
        run((ROOT/'migrations/V001__core_schema.sql').read_text(encoding='utf-8'))
        check('核心迁移成功创建 13 张表',run("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();")=='13')
        run((ROOT/'migrations/V002__optional_reports.sql').read_text(encoding='utf-8'))
        check('选做迁移成功，总计 15 张表',run("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();")=='15')
        run((ROOT/'migrations/V003__item_submission_key.sql').read_text(encoding='utf-8'))
        run((ROOT/'migrations/V004__image_cleanup_jobs.sql').read_text(encoding='utf-8'))
        check('发布防重与图片清理迁移成功，总计 16 张表',run("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();")=='16')
        run((ROOT/'seeds/S001__mock_students.sql').read_text(encoding='utf-8'))
        run((ROOT/'tests/fixtures.sql').read_text(encoding='utf-8'))
        check('虚拟名册与隔离业务数据可导入',run('SELECT COUNT(*) FROM mock_students;')=='5')

        invalid=[
          ('登录标识防止跨账号歧义',"INSERT INTO login_identifiers(identifier,user_id) VALUES ('20260001',12);",1062),
          ('名册学号唯一',"INSERT INTO mock_students(student_no,campus_card_no,real_name) VALUES ('20260001','99999999','测试人');",1062),
          ('学生身份仅绑定一个账号',"INSERT INTO users(student_record_id,nickname,email,password_hash) VALUES (1,'重复人','duplicate@example.invalid','!TEST!');",1062),
          ('邮箱忽略大小写判重',"INSERT INTO users(role,nickname,email,password_hash) VALUES ('ADMIN','重复邮箱','OWNER@EXAMPLE.INVALID','!TEST!');",1062),
          ('重复认领受唯一约束',"INSERT INTO claims(item_id,publisher_id,applicant_id,description) VALUES (101,11,12,'重复提交的测试物品特征说明内容');",1062),
          ('认领不能由发布人自己提交',"INSERT INTO claims(item_id,publisher_id,applicant_id,description) VALUES (101,11,11,'自我认领的测试物品特征说明内容');",3819),
          ('申请发布人必须匹配物品',"INSERT INTO claims(item_id,publisher_id,applicant_id,description) VALUES (101,13,14,'伪造发布人的测试物品特征说明内容');",1452),
          ('重复线索受唯一约束',"INSERT INTO clues(lost_item_id,publisher_id,provider_id,content) VALUES (102,11,12,'重复提交的测试线索说明内容足够长');",1062),
          ('线索不能由发布人自己提交',"INSERT INTO clues(lost_item_id,publisher_id,provider_id,content) VALUES (102,11,11,'自我提交的测试线索说明内容足够长');",3819),
          ('物品状态必须合法',"UPDATE items SET status='UNKNOWN' WHERE id=101;",3819),
          ('退回须有原因',"UPDATE items SET status='RETURNED',review_reason=NULL WHERE id=101;",3819),
          ('认领驳回须有原因',"UPDATE claims SET status='REJECTED',reason=NULL WHERE id=202;",3819),
          ('图片第四槽位被拒绝',"UPDATE images SET item_id=101,position=4 WHERE id=401;",3819),
          ('图片槽位不可重复',"UPDATE images SET item_id=101,position=1 WHERE id=401;",1062),
          ('绑定图片不能省略槽位',"UPDATE images SET item_id=101,position=NULL WHERE id=401;",3819),
          ('不能绑定他人上传图片',"UPDATE images SET item_id=101,position=2 WHERE id=403;",1452),
          ('图片字节上限有效',"UPDATE images SET size_bytes=2097153 WHERE id=401;",3819),
          ('图片类型约束有效',"UPDATE images SET media_type='image/gif' WHERE id=401;",3819),
          ('选中认领必须属于同一物品',"UPDATE items SET accepted_claim_id=203 WHERE id=101;",1452),
          ('授权开关仅允许布尔值',"INSERT INTO claim_contact_permissions(claim_id,grantor_id,phone_visible) VALUES (201,11,2);",3819),
          ('举报会话须属于目标物品',"INSERT INTO reports(reporter_id,item_id,claim_id,reason) VALUES (12,101,204,'测试错误关联举报');",1452),
          ('举报两类上下文互斥',"INSERT INTO reports(reporter_id,item_id,claim_id,clue_id,reason) VALUES (12,101,201,301,'测试双重上下文举报');",3819),
          ('举报处理结论不能为空',"UPDATE reports SET status='RESOLVED' WHERE id=601;",3819),
        ]
        for name,sql,code in invalid:
            error=run(sql,expected_error=code)
            check(name,True,'MySQL 拒绝，错误码 '+str(code))

        run("UPDATE claims SET status='CONFIRMED',confirmed_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=201; UPDATE items SET accepted_claim_id=201,version=version+1 WHERE id=101;")
        run("UPDATE claims SET status='CONFIRMED',confirmed_at=UTC_TIMESTAMP(6) WHERE id=202;",expected_error=1062)
        check('唯一生成列禁止两个已确认认领人',True)
        run("INSERT INTO claim_contact_permissions(claim_id,grantor_id,phone_visible,email_visible) VALUES (201,11,1,0),(201,12,0,1);")
        contact_query="""SELECT COUNT(*) FROM claims c JOIN items i ON i.id=c.item_id
          JOIN claim_contact_permissions p ON p.claim_id=c.id
          JOIN users u ON u.id=p.grantor_id
          WHERE c.id=201 AND 12 IN (c.publisher_id,c.applicant_id)
          AND p.grantor_id=IF(12=c.publisher_id,c.applicant_id,c.publisher_id)
          AND c.status='CONFIRMED' AND i.status='PUBLISHED'
          AND ((p.phone_visible=1 AND u.phone IS NOT NULL) OR p.email_visible=1);"""
        check('有效阶段且对方授权时查询可获得一条授权',run(contact_query)=='1')
        completion="""START TRANSACTION;
          SELECT id FROM users WHERE id IN (11,12) ORDER BY id FOR UPDATE;
          SELECT id FROM items WHERE id=101 FOR UPDATE;
          UPDATE claims SET status='COMPLETED',completed_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=201 AND status='CONFIRMED';
          UPDATE items SET status='ARCHIVED',closure_reason='HANDOVER',closed_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=101;
          UPDATE claim_contact_permissions SET phone_visible=0,email_visible=0,version=version+1 WHERE claim_id=201;
        """
        run(completion+'INSERT INTO deliberately_missing_table VALUES (1); COMMIT;',expected_error=1146)
        check('交接途中失败全部回滚',run("SELECT CONCAT(c.status,':',i.status,':',p.phone_visible) FROM claims c JOIN items i ON i.id=c.item_id JOIN claim_contact_permissions p ON p.claim_id=c.id AND p.grantor_id=11 WHERE c.id=201;")=='CONFIRMED:PUBLISHED:1')
        run(completion+"INSERT INTO operation_logs(actor_id,entity_type,entity_id,action,before_status,after_status,request_id) VALUES (11,'CLAIM',201,'COMPLETE','CONFIRMED','COMPLETED','db-validation'); COMMIT;")
        check('交接完成同时归档与撤回两方授权',run("SELECT COUNT(*) FROM claim_contact_permissions WHERE claim_id=201 AND phone_visible=0 AND email_visible=0 AND version=2;")=='2')
        check('交接完成后授权查询无结果',run(contact_query)=='0')
        run("UPDATE claim_contact_permissions SET phone_visible=1 WHERE claim_id=201 AND grantor_id=11;")
        check('即使残留授权开关，业务状态仍阻止读取',run(contact_query)=='0')
        run("UPDATE claim_contact_permissions SET phone_visible=0 WHERE claim_id=201;")

        # 两个真实数据库连接并发确认同一物品的不同申请。
        def confirm_sql(claim_id):
            return f"""START TRANSACTION;
              SELECT id FROM items WHERE id=105 FOR UPDATE;
              UPDATE claims c JOIN items i ON i.id=c.item_id SET c.status='CONFIRMED',c.confirmed_at=UTC_TIMESTAMP(6),c.version=c.version+1
                WHERE c.id={claim_id} AND c.status='PENDING' AND i.status='PUBLISHED' AND i.accepted_claim_id IS NULL;
              SET @won=ROW_COUNT();
              UPDATE items SET accepted_claim_id={claim_id},version=version+1 WHERE id=105 AND @won=1;
              UPDATE claims SET status='CLOSED',reason='OTHER_ACCEPTED',version=version+1 WHERE item_id=105 AND id<>{claim_id} AND status='PENDING' AND @won=1;
              COMMIT; SELECT @won;"""
        with ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(run,[confirm_sql(205),confirm_sql(206)]))
        outcomes=sorted(x.splitlines()[-1] for x in results)
        check('两连接并发确认仅一人成功',outcomes==['0','1'],str(outcomes))
        check('并发后仅一个确认和一个关闭',run("SELECT GROUP_CONCAT(status ORDER BY status) FROM claims WHERE item_id=105;")=='CLOSED,CONFIRMED')

        # 线索提交与归档串行化；归档先执行时插入零条，插入先执行则保留历史。
        clue_sql="""START TRANSACTION; SELECT id FROM items WHERE id=106 FOR UPDATE;
          INSERT INTO clues(lost_item_id,publisher_id,provider_id,content)
          SELECT id,publisher_id,12,'用于并发验证的一条足够长的线索内容' FROM items WHERE id=106 AND type='LOST' AND status='PUBLISHED';
          COMMIT;"""
        close_sql="""START TRANSACTION; SELECT id FROM items WHERE id=106 FOR UPDATE;
          UPDATE items SET status='ARCHIVED',closure_reason='FOUND',closed_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=106 AND status='PUBLISHED'; COMMIT;"""
        with ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(run,[clue_sql,close_sql]))
        check('线索与归档并发后物品已归档',run('SELECT status FROM items WHERE id=106;')=='ARCHIVED')
        count_before=run('SELECT COUNT(*) FROM clues WHERE lost_item_id=106;')
        run(clue_sql)
        check('归档后的条件插入不新增线索',run('SELECT COUNT(*) FROM clues WHERE lost_item_id=106;')==count_before)

        # 保存信息模式用于数据字典；这不是对现有用户数据库的检查。
        columns=run("""SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,
          COALESCE(COLUMN_DEFAULT,'<NULL>'),EXTRA,REPLACE(REPLACE(COLUMN_COMMENT,'\\t',' '),'\\n',' '),ORDINAL_POSITION
          FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION;""")
        indexes=run("""SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,COALESCE(COLLATION,'')
          FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;""")
        fks=run("""SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME
          FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION;""")
        table_comments=run("SELECT TABLE_NAME,TABLE_COMMENT FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY TABLE_NAME;")
        metadata={'columns':[x.split('\t') for x in columns.splitlines()], 'indexes':[x.split('\t') for x in indexes.splitlines()], 'foreignKeys':[x.split('\t') for x in fks.splitlines()], 'tables':[x.split('\t') for x in table_comments.splitlines()]}
        (ROOT/'validation_metadata.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
        lines=['# 校园失物招领系统数据库字段字典','','版本：0.1.0-draft。由 MySQL 8.0.34 隔离实例执行迁移后的 information_schema 提取。字段与索引以建表脚本为准；不是现有业务库的导出。','',
               '全部业务时间 DATETIME(6) 按 UTC 读写；API 使用 camelCase，SQL 使用 snake_case。BIGINT ID 经 Java Long 处理，对外序列化为字符串。生成列不可由应用写入。','']
        for table,comment in metadata['tables']:
            lines+=['## '+table,'',comment,'','| 字段 | MySQL 类型 | 可空 | 默认值 | 特性 | 说明 |','| --- | --- | --- | --- | --- | --- |']
            for row in metadata['columns']:
                if row[0]==table:
                    lines.append('| '+' | '.join(str(v).replace('&','&amp;').replace('<','&lt;').replace('>','&gt;').replace('|','&#124;') for v in row[1:7])+' |')
            lines+=['','索引：','']
            grouped={}
            for t,name,nonunique,seq,col,direction in metadata['indexes']:
                if t==table:grouped.setdefault((name,nonunique),[]).append(col+(' DESC' if direction=='D' else ''))
            for (name,nonunique),cols in grouped.items():lines.append('- `'+name+'`：'+('唯一；' if nonunique=='0' else '')+'`'+', '.join(cols)+'`。')
            related=[r for r in metadata['foreignKeys'] if r[0]==table]
            if related:
                lines+=['','外键（复合外键按同名约束合并阅读，全部 RESTRICT）：','']
                for _,name,col,parent,pcol in related:lines.append(f'- `{name}`：`{col}` → `{parent}.{pcol}`。')
            lines+=['']
        (DOCS/'数据库字段字典.md').write_text('\n'.join(lines),encoding='utf-8')
        check('真实数据库元数据已生成数据字典',len(metadata['tables'])==16)
        report['status']='passed'
        report['tableCount']=16
        report['limitations']=['仅隔离 MySQL DDL、约束和 SQL 事务样例验证；不是 Spring Boot 后端测试。','角色权限、会话、验证码真实投递、文件内容、跨表业务类型限制仍由服务实现并测试。','未进行 100 并发/1 万条数据性能验收。','未连接、建库或修改原有 MySQL 服务；未创建真实可登录账号。']
    except Exception as exc:
        report['error']=str(exc)
        raise
    finally:
        try:
            subprocess.run([str(mysqladmin),'--no-defaults','--protocol=TCP','--host=127.0.0.1','--port='+str(port),'--user=root','shutdown'],capture_output=True,creationflags=flags,timeout=10)
            proc.wait(timeout=15)
        except Exception:
            if proc.poll() is None:proc.terminate()
            try:proc.wait(timeout=5)
            except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=5)
        report['isolatedServerStopped']=proc.poll() is not None
        report['passedChecks']=len(CHECKS)
        (DOCS/'数据库设计验证结果.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'status':report['status'],'checks':len(CHECKS),'isolatedServerStopped':report['isolatedServerStopped'],'report':str(DOCS/'数据库设计验证结果.json')},ensure_ascii=False),flush=True)

if __name__=='__main__':main()
