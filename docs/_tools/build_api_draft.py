"""本轮接口初稿生成工具；不构建应用、不连接数据库。

生成结果供人工评审；后续若直接编辑 openapi.yaml，不应无审阅重跑此工具覆盖。
用 --validate-only 检查已有合同，不重新生成。
"""
from pathlib import Path
import csv
import json
import os
import re
import sys

sys.path.insert(0, str(Path(os.environ['TEMP']) / 'campus-api-doc-validation'))
import yaml
from openapi_spec_validator import validate_spec
from openapi_schema_validator import OAS30Validator

ROOT = Path(__file__).resolve().parents[1]
S = {}
OPS = []

def ref(name):
    return {'$ref': '#/components/schemas/' + name}

def string(desc='', **kw):
    return {'type': 'string', 'description': desc, **kw}

def integer(desc='', **kw):
    return {'type': 'integer', 'description': desc, **kw}

def boolean(desc=''):
    return {'type': 'boolean', 'description': desc}

def obj(props, required=None, desc='', **kw):
    out = {'type': 'object', 'description': desc, 'additionalProperties': False,
           'properties': props, **kw}
    if required is None:
        required = list(props)
    if required:
        out['required'] = required
    return out

def array(schema, **kw):
    return {'type': 'array', 'items': schema, **kw}

ID = string('正整数 ID，以字符串传输，不在前端转为 Number。', pattern=r'^[1-9][0-9]*$', example='1001')
TS = string('带时区的 ISO 8601 时间；服务端输出 UTC，页面按北京时间展示。', format='date-time')
DATE = string('按 Asia/Shanghai 判断的业务日期，不能晚于当天。', format='date')
VERSION = integer('当前资源版本，从 1 开始；更新成功后递增。', minimum=1)
EV = integer('调用方最后读取的目标资源 version；不一致返回 409 VERSION_CONFLICT。', minimum=1)
EMAIL = string('ASCII 邮箱地址；去首尾空白后按小写规范化，学校邮箱域名由项目配置决定。', format='email', maxLength=254, pattern=r'^[\x21-\x7E]+$')
PASSWORD = string('至少 8 字符且含字母和数字；草案上限 64 字符，不能记录到日志。', minLength=8, maxLength=64, pattern=r'^(?=.*[A-Za-z])(?=.*[0-9]).+$', writeOnly=True)
REASON = string('去除首尾空白后必填；草案上限 500 字。', minLength=1, maxLength=500)
ITEM_STATES = ['PENDING_REVIEW', 'RETURNED', 'PUBLISHED', 'OFF_SHELF', 'ARCHIVED']
CLAIM_STATES = ['PENDING', 'CONFIRMED', 'REJECTED', 'COMPLETED', 'CLOSED']
CATEGORIES = ['DOCUMENT', 'ELECTRONICS', 'DAILY', 'BOOK', 'CLOTHING', 'OTHER']
S['Id'] = ID
S['ItemStatus'] = string('物品审核与发布状态。', enum=ITEM_STATES)
S['ClaimStatus'] = string('CLOSED 为本草案补充的系统关闭状态，不等同于发布人驳回。', enum=CLAIM_STATES)
S['EmptyData'] = obj({}, desc='操作成功，无额外数据。')
S['FieldError'] = obj({'field': string('字段路径。'), 'message': string('字段错误说明。')})
S['ErrorResponse'] = obj({
    'code': string('稳定的业务错误码，见接口文档错误码表。'),
    'message': string('可向用户展示的错误说明，不包含内部堆栈。'),
    'data': {'type': 'object', 'nullable': True, 'enum': [None], 'description': '错误响应固定为 null。'},
    'requestId': string('请求追踪编号。'), 'errors': array(ref('FieldError')),
})
S['CsrfData'] = obj({'headerName': string('固定请求头。', enum=['X-CSRF-TOKEN']), 'token': string('与会话绑定的 CSRF 令牌。')})
S['SendCodeRequest'] = obj({'email': EMAIL})
S['CodeSentData'] = obj({'expiresInSeconds': integer('有效期。', enum=[300]), 'retryAfterSeconds': integer('再次发送等待时间，草案 60 秒。', enum=[60])})
S['RegisterRequest'] = obj({
    'studentId': string('数字学号；实际学校位数待确定，当前结构上限 32。', pattern=r'^[0-9]+$', maxLength=32),
    'realName': string('与模拟学生名册一致，2～10 个汉字。', minLength=2, maxLength=10, pattern=r'^[\u3400-\u9fff]+$'),
    'campusCardNo': string('唯一校园卡号；实际格式待确认，当前 1～32 个 ASCII 可见字符。', minLength=1, maxLength=32, pattern=r'^[\x21-\x7E]+$'),
    'email': EMAIL, 'emailCode': string('注册用途验证码。', pattern=r'^[0-9]{6}$'), 'password': PASSWORD,
    'nickname': string('可选公开昵称；未填写使用不暴露真实身份的默认昵称。', minLength=1, maxLength=30),
}, ['studentId', 'realName', 'campusCardNo', 'email', 'emailCode', 'password'])
S['LoginRequest'] = obj({'account': string('学号、校园卡号或预置管理员账号；统一登录标识表排除跨字段身份歧义。', minLength=1, maxLength=32, pattern=r'^[\x21-\x7E]+$'), 'password': string('登录密码。', minLength=1, maxLength=64, writeOnly=True)})
S['Me'] = obj({'id': ID, 'studentId': string('自己的学号。'), 'realName': string('自己的姓名。'), 'campusCardNo': string('自己的卡号。'), 'nickname': string('公开昵称。'), 'email': EMAIL, 'phone': string('自己的手机号，未填写为 null。', nullable=True, maxLength=20), 'role': string('管理员由预置账号产生，注册不能指定角色。', enum=['STUDENT', 'ADMIN']), 'version': VERSION})
for field in ['studentId','realName','campusCardNo']:
    S['Me']['properties'][field]['nullable'] = True
    S['Me']['properties'][field]['description'] += '学生从绑定名册取得；预置管理员为 null。'
S['ProfilePatchRequest'] = obj({'expectedVersion': EV, 'nickname': string('昵称。', minLength=1, maxLength=30), 'phone': string('手机号；null 表示清空。', nullable=True, pattern=r'^\+?[0-9 -]{6,20}$', maxLength=20)}, ['expectedVersion'], minProperties=2)
S['ChangeEmailRequest'] = obj({'expectedVersion': EV, 'email': EMAIL, 'emailCode': string('绑定到当前用户和新邮箱的修改邮箱验证码。', pattern=r'^[0-9]{6}$')})
S['ChangePasswordRequest'] = obj({'oldPassword': string('原密码。', minLength=1, maxLength=64, writeOnly=True), 'newPassword': PASSWORD})
S['VersionRequest'] = obj({'expectedVersion': EV})
S['ReasonVersionRequest'] = obj({'expectedVersion': EV, 'reason': REASON})
S['Image'] = obj({'id': ID, 'url': string('受物品可见性控制的图片地址，例如 /api/v1/images/501。'), 'mediaType': string(enum=['image/jpeg', 'image/png']), 'sizeBytes': integer('实际字节数。', minimum=1, maximum=2097152)})
S['UploadImageRequest'] = obj({'file': string('JPG/PNG；校验真实内容，大小 1～2097152 字节。', format='binary')})
S['Publisher'] = obj({'id': ID, 'nickname': string('只返回昵称，不返回真实姓名、学号、联系方式。')})
item_fields = {'type': string('发布后不允许修改类型。', enum=['LOST', 'FOUND']), 'category': string(enum=CATEGORIES), 'location': string('地点，去除首尾空白。', minLength=1, maxLength=50), 'occurredDate': DATE, 'title': string('标题。', minLength=1, maxLength=50), 'description': string('物品说明，不允许包含联系方式。', minLength=10, maxLength=500), 'imageIds': array(ID, maxItems=3, uniqueItems=True, description='当前用户上传且未被其他物品绑定的图片。缺省无图。')}
S['CreateItemRequest'] = obj(item_fields, ['type', 'category', 'location', 'occurredDate', 'title', 'description'])
S['EditItemRequest'] = obj({'expectedVersion': EV, **{k:v for k,v in item_fields.items() if k != 'type'}}, ['expectedVersion'], minProperties=2, desc='PATCH 只更新给出的字段；imageIds 为完整替换，空数组删除所有关联。编辑成功重回待审核。')
public_item = {'id': ID, 'type': item_fields['type'], 'category': item_fields['category'], 'location': item_fields['location'], 'occurredDate': DATE, 'title': item_fields['title'], 'description': item_fields['description'], 'images': array(ref('Image'), maxItems=3), 'publisher': ref('Publisher'), 'publishedAt': {**TS, 'nullable': True}, 'createdAt': TS, 'hasConfirmedClaim': boolean('招领信息是否已有确认人；不暴露申请编号或申请人身份。')}
S['PublicItem'] = obj(public_item, desc='仅公开字段；列表/详情只返回 PUBLISHED 物品。')
S['ItemRecord'] = obj({**public_item, 'status': ref('ItemStatus'), 'version': VERSION, 'reviewReason': string('最近一次退回原因，无则 null。', nullable=True), 'closureReason': string('关闭原因。', nullable=True, enum=['FOUND', 'HANDOVER', 'OWNER_WITHDRAWN', 'VIOLATION', None]), 'updatedAt': TS}, desc='自己的发布、管理员审核及私有参与者详情中的物品记录；不包含联系方式。')
S['ClaimRequest'] = obj({'description': string('认领特征说明。上限 500 字为草案设计值。', minLength=10, maxLength=500)})
S['Claim'] = obj({'id': ID, 'itemId': ID, 'applicant': ref('Publisher'), 'publisher': ref('Publisher'), 'description': string('认领凭证，不公开。'), 'status': ref('ClaimStatus'), 'reason': string('驳回或关闭原因，无则 null。', nullable=True), 'version': VERSION, 'createdAt': TS, 'updatedAt': TS})
S['ClaimDetail'] = obj({'claim': ref('Claim'), 'item': ref('ItemRecord')}, desc='归档后双方仍能在此查看相关物品，不依赖公开详情接口。')
S['MessageRequest'] = obj({'content': string('纯文本，1～1000 字为草案限制；禁止 HTML 和联系方式，交换联系方式使用授权入口。', minLength=1, maxLength=1000)})
S['Message'] = obj({'id': ID, 'sender': ref('Publisher'), 'content': string('纯文本。'), 'createdAt': TS})
S['ClueRequest'] = obj({'content': string('文字线索，不包含联系方式；不支持图片或关联招领编号。', minLength=10, maxLength=500)})
S['Clue'] = obj({'id': ID, 'lostItemId': ID, 'provider': ref('Publisher'), 'publisher': ref('Publisher'), 'content': string('只向双方返回的线索。'), 'createdAt': TS})
S['ClueDetail'] = obj({'clue': ref('Clue'), 'item': ref('ItemRecord')})
S['ContactPermissionRequest'] = obj({'expectedVersion': integer('首次设置为 0，后续使用查询到的授权版本。', minimum=0), 'phoneVisible': boolean('是否授权该申请另一方查看自己的当前手机号。'), 'emailVisible': boolean('是否授权该申请另一方查看自己的当前邮箱。')})
S['ContactPermission'] = obj({'claimId': ID, 'phoneVisible': boolean('原始授权选择。'), 'emailVisible': boolean('原始授权选择。'), 'effective': boolean('仅 claim 为 CONFIRMED 且物品仍有效时为 true。'), 'version': integer('未设置时为 0。', minimum=0), 'updatedAt': {**TS, 'nullable': True}}, desc='仅返回自己的授权设置，不含实际联系方式；完成后 effective=false。')
S['CounterpartyContact'] = obj({'claimId': ID, 'phone': string('对方授权的当前手机号。', maxLength=20), 'email': EMAIL}, ['claimId'], desc='只出现已授权且实际存在的字段；不可见字段省略，不返回掩码或 null 占位。至少一种联系方式被授权才能成功。')
S['ReportRequest'] = obj({'itemId': ID, 'claimId': ID, 'clueId': ID, 'reason': REASON}, ['itemId', 'reason'], desc='claimId 与 clueId 最多填写一个；必须属于 itemId，且举报人必须是该会话参与者。不提供时只举报公开物品。')
S['ReportRequest']['not'] = {'required': ['claimId', 'clueId']}
S['Report'] = obj({'id': ID, 'itemId': ID, 'claimId': {**ID, 'nullable': True}, 'clueId': {**ID, 'nullable': True}, 'reason': REASON, 'status': string(enum=['PENDING', 'RESOLVED']), 'action': string(nullable=True, enum=['KEEP', 'TAKE_DOWN', 'REJECT_PENDING_CLAIM', None]), 'resolution': string('处理结论。', nullable=True), 'version': VERSION, 'createdAt': TS, 'resolvedAt': {**TS, 'nullable': True}})
S['ResolveReportRequest'] = obj({'expectedVersion': EV, 'action': string(enum=['KEEP', 'TAKE_DOWN', 'REJECT_PENDING_CLAIM']), 'reason': REASON}, desc='仅可驳回尚未确认且与举报直接关联的申请；已确认申请不能用此操作直接回退。')
S['ReviewRecord'] = obj({'id': ID, 'itemId': ID, 'action': string(enum=['APPROVE', 'RETURN', 'TAKE_DOWN']), 'reason': string(nullable=True), 'operatorId': ID, 'createdAt': TS})
S['ReportConversation'] = obj({'kind': string(enum=['CLAIM', 'CLUE']), 'contextId': ID, 'evidenceText': string('举报所关联的认领说明或线索内容。'), 'items': array(ref('Message')), 'page': integer(minimum=1), 'pageSize': integer(minimum=1, maximum=50), 'total': integer(minimum=0)}, desc='管理员通过举报上下文访问，必须记录访问审计，不返回联系方式。')

def envelope(name):
    key = name + 'Response'
    if key not in S:
        S[key] = obj({'code': string(enum=['OK']), 'message': string('提示语，客户端不依赖具体文案分支。'), 'data': ref(name), 'requestId': string('请求追踪编号。')})
    return ref(key)

def page(name):
    key = name + 'Page'
    if key not in S:
        S[key] = obj({'items': array(ref(name)), 'page': integer(minimum=1), 'pageSize': integer(minimum=1, maximum=50), 'total': integer(minimum=0)})
    return key

ERRORS = {
 '400': ['VALIDATION_ERROR','IDENTITY_MISMATCH','EMAIL_CODE_INVALID','EMAIL_CODE_EXPIRED','EMAIL_CODE_USED','OLD_PASSWORD_INCORRECT','CONTACT_FIELD_MISSING','REPORT_CONTEXT_INVALID'],
 '401': ['AUTH_REQUIRED','BAD_CREDENTIALS'],
 '403': ['FORBIDDEN','CSRF_INVALID','SELF_OPERATION_NOT_ALLOWED','CONTACT_NOT_AUTHORIZED'],
 '404': ['RESOURCE_NOT_FOUND'],
 '409': ['VERSION_CONFLICT','IDENTITY_ALREADY_BOUND','ACCOUNT_IDENTIFIER_CONFLICT','EMAIL_ALREADY_BOUND','ITEM_STATE_CONFLICT','CLAIM_ALREADY_EXISTS','ITEM_ALREADY_CLAIMED','CLAIM_STATE_CONFLICT','CLUE_ALREADY_EXISTS','CONTACT_NOT_ACTIVE','CONVERSATION_CLOSED','IMAGE_ALREADY_BOUND','REPORT_STATE_CONFLICT'],
 '413': ['IMAGE_TOO_LARGE'], '415': ['UNSUPPORTED_MEDIA_TYPE'], '429': ['RATE_LIMITED'], '500': ['INTERNAL_ERROR'],
}
ERROR_DESC = {'400':'参数或业务输入不合法','401':'未登录、会话失效或凭据错误','403':'权限不足、CSRF 校验失败或尚未授权','404':'不存在或对当前访问者不可见','409':'版本、状态或唯一性冲突','413':'图片大小超限','415':'请求或文件类型不支持','429':'请求过于频繁','500':'服务端内部错误'}

def q(name, schema, desc=''):
    return {'name': name, 'in':'query', 'required': False, 'description': desc or schema.get('description','查询条件。'), 'schema': schema}

PAGE = [q('page', integer(minimum=1, default=1)), q('pageSize', integer(minimum=1, maximum=50, default=10))]
PUBLIC_PAGE = [q('page', integer(minimum=1, default=1)), q('pageSize', integer(enum=[10], default=10))]
FILTERS = [q('type', item_fields['type']), q('category', item_fields['category']), q('location', string(maxLength=50), '地点包含匹配。'), q('keyword', string(maxLength=100), '标题或描述包含匹配；% 与 _ 视为普通字符。'), q('dateFrom', string(format='date'), '发生日期起点，包含。'), q('dateTo', string(format='date'), '发生日期终点，包含；不得早于 dateFrom。')]

def op(method, path, oid, title, tag, owner, reqs, output='EmptyData', body=None,
       public=False, params=None, status='200', desc='', rules=None, errors=None,
       optional=False, media='application/json', binary=False):
    p = []
    for key in re.findall(r'\{([^}]+)\}',path):
        p.append({'name':key,'in':'path','required':True,'description':'目标资源编号。','schema':ref('Id')})
    p += params or []
    if method not in ['get','head','options']:
        p.append({'$ref':'#/components/parameters/CsrfHeader'})
    codes = set(['500']) | set(errors or [])
    if not public: codes |= {'401','403'}
    if method != 'get': codes |= {'400','403'}
    if p: codes.add('400')
    if body: codes.add('415')
    if '{' in path: codes.add('404')
    headers = {'Cache-Control': {'schema': {'type':'string'}, 'description':'所有响应使用 no-store；特别是私有详情、验证码、CSRF 和联系方式。'}}
    response = {'description':'成功。' + ('创建资源。' if status=='201' else ''), 'headers':headers}
    if binary:
        response['content'] = {m:{'schema':{'type':'string','format':'binary'}} for m in ['image/jpeg','image/png']}
    else:
        response['content'] = {'application/json':{'schema':envelope(output)}}
    if oid in ['getCsrf','login','logout','changePassword']:
        response['headers']['Set-Cookie'] = {'schema':{'type':'string'},'description':'会话创建、登录轮换或会话注销时由服务端设置 JSESSIONID；HttpOnly、SameSite=Lax，HTTPS 环境加 Secure。'}
    responses={status:response, **{code:{'$ref':'#/components/responses/Error'+code} for code in sorted(codes)}}
    value={'operationId':oid,'summary':title,'description':desc,'tags':[tag], 'parameters':p,
           'security':[] if public else [{'SessionAuth':[]}], 'responses':responses,
           'x-owner':owner,'x-requirement-ids':reqs,'x-rule-ids':rules or [],
           'x-scope':'optional' if optional else 'core', 'x-contract-status':'draft',
           'x-test-ids':['CT-'+oid+'-01']}
    if body:
        value['requestBody']={'required':True,'content':{media:{'schema':ref(body)}}}
        examples={
            'ContactPermissionRequest': {'expectedVersion':0,'phoneVisible':True,'emailVisible':False},
            'ClaimRequest': {'description':'黑色水杯的杯底有一道明显划痕'},
            'ClueRequest': {'content':'我在图书馆三楼服务台看到类似的水杯'},
            'VersionRequest': {'expectedVersion':1},
            'ReasonVersionRequest': {'expectedVersion':1,'reason':'物品特征与所描述的情况不一致'},
            'MessageRequest': {'content':'可以补充说明一下杯底的标记吗'},
        }
        if body in examples:
            value['requestBody']['content'][media]['example']=examples[body]
    OPS.append((method,path,value,output,body,status))

op('get','/auth/csrf','getCsrf','获取 CSRF 令牌','认证','甲',['SUP-01'],output='CsrfData',public=True,desc='匿名也可调用；必要时建立匿名会话，不产生已登录身份。登录、退出或改密后重新获取令牌。',rules=['BR-01'])
op('post','/auth/email-codes','sendRegistrationCode','发送注册验证码','认证','甲',['FR-01'],'CodeSentData','SendCodeRequest',public=True,errors=['429'],desc='6 位数字，5 分钟有效，绑定注册用途与邮箱；重发替换旧码，成功注册时原子消费。草案每邮箱与来源组合限制发送频率。演示邮件接收方式见 P-01。',rules=['BR-02'])
op('post','/auth/register','register','注册并绑定校园身份','认证','甲',['FR-01'],'Me','RegisterRequest',public=True,status='201',errors=['409'],desc='仅创建学生账号，不自动登录。模拟名册匹配、邮箱验证码和唯一性检查通过后原子创建。拒绝未知 role 字段。手机号可在个人资料补充。',rules=['BR-02'])
op('post','/auth/login','login','登录','认证','甲',['FR-02'],'Me','LoginRequest',public=True,errors=['401','429'],desc='成功轮换会话 ID，返回当前用户；邮箱不作为登录账号。学号与卡号跨列冲突必须在建号时排除。登录失败不永久锁号，可临时限流。')
op('post','/auth/logout','logout','退出登录','认证','甲',['FR-02'],desc='使当前会话失效，清除会话 Cookie；旧会话访问私有接口返回 401。')
op('get','/me','getMe','查询自己的资料','个人中心','甲',['FR-06'],'Me',desc='不接受 userId 查询参数。')
op('patch','/me','updateProfile','修改昵称和手机号','个人中心','甲',['FR-06'],'Me','ProfilePatchRequest',errors=['409'],desc='学号、姓名、校园卡和角色不可修改；邮箱通过独立验证码流程更改。变更实际联系方式时，在同一事务中撤回该用户对应字段的全部既有授权，防止静默分享新值。',rules=['BR-07'])
op('post','/me/email-codes','sendChangeEmailCode','发送修改邮箱验证码','个人中心','甲',['FR-06'],'CodeSentData','SendCodeRequest',errors=['429'],desc='验证码绑定当前用户、新邮箱和修改邮箱用途，不能用于注册。')
op('put','/me/email','changeEmail','确认修改邮箱','个人中心','甲',['FR-06'],'Me','ChangeEmailRequest',errors=['409'],desc='检查唯一性与验证码后更新；撤回该用户所有 emailVisible 授权，记录版本变化。',rules=['BR-07'])
op('put','/me/password','changePassword','修改密码','个人中心','甲',['FR-03'],body='ChangePasswordRequest',desc='原密码验证通过后修改，旧密码不可再登录；草案使该用户全部登录会话失效，修改者也须重新登录。')
op('get','/me/items','listMyItems','查询我的发布','个人中心','甲',['FR-04'],'ItemRecordPage',params=PAGE+[q('status',ref('ItemStatus'))],desc='包含待审核、退回、下架和归档记录；createdAt、id 降序，只返回当前用户的数据。')
op('get','/me/items/{itemId}','getMyItem','查询我的发布详情','个人中心','甲',['FR-04'],'ItemRecord',desc='归属不匹配统一 404；用于待审核或归档后的个人管理。')
op('get','/me/claims','listMyClaims','查询我提交的认领','个人中心','甲',['FR-05'],'ClaimPage',params=PAGE+[q('status',ref('ClaimStatus'))],desc='只查询 applicant 为当前用户；createdAt、id 降序。')
op('get','/me/received-claims','listReceivedClaims','查询我收到的认领','个人中心','甲',['FR-15','FR-28'],'ClaimPage',params=PAGE+[q('itemId',ID),q('status',ref('ClaimStatus'))],desc='只查询属于当前发布人的物品申请；过滤 itemId 不得扩大归属范围。')
op('get','/me/clues','listMyClues','查询我提供的线索','个人中心','甲',['FR-25'],'CluePage',params=PAGE,desc='只返回自己的线索，createdAt、id 降序。')
op('get','/me/received-clues','listReceivedClues','查询我收到的线索','个人中心','甲',['FR-25'],'CluePage',params=PAGE+[q('itemId',ID)],desc='只返回自己的寻物信息收到的线索；createdAt、id 降序。')
op('post','/uploads/images','uploadImage','上传物品图片','发布与查找','乙',['FR-10'],'Image','UploadImageRequest',status='201',media='multipart/form-data',errors=['413','415','429'],desc='按文件内容检查 JPG/PNG，不信任扩展名；最大 2097152 字节。产生仅上传者可访问的未绑定图片，发布时最多绑定 3 张。临时文件清理不构成用户删除接口。',rules=['BR-03'])
op('get','/images/{imageId}','getImage','获取图片内容','发布与查找','乙',['FR-10','FR-14'],'EmptyData',public=True,binary=True,desc='仅绑定 PUBLISHED 物品的图片可匿名读取。其他情况须会话且为上传者、物品所有者、相关申请/线索参与者或有审核职责的管理员；未绑定图片仅上传者可看。无权统一 404，存储目录不直接公开。',rules=['BR-03'])
op('post','/items','createItem','发布物品信息','发布与查找','乙',['FR-07','FR-10'],'ItemRecord','CreateItemRequest',status='201',errors=['409'],desc='作者取自当前会话。状态 PENDING_REVIEW，version=1；验证图片归属和唯一绑定、日期及文本。',rules=['BR-03','BR-04'])
op('patch','/items/{itemId}','editItem','编辑并重新提交审核','发布与查找','乙',['FR-08'],'ItemRecord','EditItemRequest',errors=['409'],desc='仅本人；允许 PENDING_REVIEW、RETURNED、PUBLISHED。FOUND 存在任何认领历史、LOST 存在任何线索历史时禁止编辑（草案）。成功重回待审核，公共接口立即不可见；已下架、归档不能编辑。',rules=['BR-04'])
op('post','/items/{itemId}/withdraw','withdrawItem','主动下架或关闭寻找','发布与查找','乙',['FR-09'],'ItemRecord','VersionRequest',errors=['409'],desc='本人且非下架/归档。FOUND 处于 CONFIRMED 时禁止主动下架；其余待确认申请原子转 CLOSED。LOST 可关闭，保留线索，停发留言。closureReason=OWNER_WITHDRAWN。',rules=['BR-04','BR-05','BR-06'])
op('get','/items','listPublicItems','公开列表与搜索','发布与查找','乙',['FR-11','FR-12','FR-13'],'PublicItemPage',public=True,params=PUBLIC_PAGE+FILTERS,desc='只返回 PUBLISHED；固定每页 10 条。所有筛选条件 AND 组合，keyword 匹配标题 OR 描述。publishedAt、id 降序；无结果返回 items=[]、total=0。')
op('get','/items/{itemId}','getPublicItem','公开物品详情','发布与查找','乙',['FR-14'],'PublicItem',public=True,desc='仅 PUBLISHED 可读，其余即使发布者登录也返回 404，发布者改用 /me/items/{itemId}。无联系方式、凭证或线索内容。')
op('post','/items/{itemId}/claims','createClaim','提交认领申请','认领','丙',['FR-15'],'Claim','ClaimRequest',status='201',errors=['409'],desc='仅 PUBLISHED 的 FOUND，不能是发布人，不能已有确认人。同一用户对同一物品最多一个申请（含驳回/关闭历史）。申请成功即允许双方留言。',rules=['BR-05','BR-08'])
op('get','/claims/{claimId}','getClaim','认领私有详情','认领','丙',['FR-05','FR-15'],'ClaimDetail',desc='仅申请人和发布人；其他人包括无关联举报上下文的管理员返回 404。含归档物品记录，不返回联系方式。')
op('post','/claims/{claimId}/confirm','confirmClaim','确认认领人','认领','丙',['FR-16'],'Claim','VersionRequest',errors=['409'],desc='仅发布人，申请 PENDING、物品 PUBLISHED 且未被占用。事务内原子设置 acceptedClaimId、当前申请 CONFIRMED、其他 PENDING 申请 CLOSED/OTHER_ACCEPTED，并递增版本。并发确认只能一个成功。',rules=['BR-05','BR-09'])
op('post','/claims/{claimId}/reject','rejectClaim','驳回认领','认领','丙',['FR-17'],'Claim','ReasonVersionRequest',errors=['409'],desc='仅发布人，PENDING 转 REJECTED；必须给原因。不能用于取消已确认认领。',rules=['BR-05'])
op('post','/claims/{claimId}/complete','completeClaim','确认交接完成','认领','丙',['FR-18'],'Claim','VersionRequest',errors=['409'],desc='仅发布人；CONFIRMED 且为物品唯一确认申请、物品 PUBLISHED。事务内申请 COMPLETED、物品 ARCHIVED/HANDOVER、撤销双方联系方式授权并增加授权版本；停止发送留言，保留历史。',rules=['BR-05','BR-07','BR-08','BR-09'])
for kind,tag,owner,reqs in [('claims','认领','丙',['FR-19']),('clues','线索','乙',['FR-24'])]:
    singular=kind[:-1]
    op('get',f'/{kind}/{{{singular}Id}}/messages','list'+singular.title()+'Messages','查询私有留言',tag,owner,reqs,'MessagePage',params=PAGE,desc='仅双方，createdAt、id 升序；终态仍可查询。无关人员返回 404，管理员使用关联举报专用接口。',rules=['BR-08'])
    op('post',f'/{kind}/{{{singular}Id}}/messages','send'+singular.title()+'Message','发送私有留言',tag,owner,reqs,'Message','MessageRequest',status='201',errors=['409','429'],desc=('认领 PENDING/CONFIRMED 且物品仍 PUBLISHED 时双方可发。' if kind=='claims' else '线索创建后且寻物信息仍 PUBLISHED 时双方可发。')+'终态返回 CONVERSATION_CLOSED；纯文本，不准通过内容夹带联系方式。',rules=['BR-08'])
op('get','/claims/{claimId}/contact-permission','getMyContactPermission','查询自己的授权设置','联系方式','丙',['FR-06'],'ContactPermission',desc='仅参与者，尚未设置返回 version=0、两个选择 false、updatedAt=null。终态允许查自己的设置，但 effective=false，不返回实际联系方式。',rules=['BR-07'])
op('put','/claims/{claimId}/contact-permission','setMyContactPermission','设置或撤回自己的授权','联系方式','丙',['FR-06'],'ContactPermission','ContactPermissionRequest',errors=['409'],desc='仅参与者且申请 CONFIRMED、物品 PUBLISHED；更新当前用户向另一方的授权，两字段 false 即撤回。不接受任意用户 ID。授权某字段前须已填写。expectedVersion 防止旧页面覆盖撤回结果。',rules=['BR-07','BR-09'])
op('get','/claims/{claimId}/counterparty-contact','getCounterpartyContact','获取对方授权的联系方式','联系方式','丙',['FR-06'],'CounterpartyContact',errors=['409'],desc='仅该认领参与者；先检查 CONFIRMED 和物品 PUBLISHED，再检查对方对应授权。非有效阶段返回 409 CONTACT_NOT_ACTIVE；有效阶段无授权返回 403 CONTACT_NOT_AUTHORIZED。交接完成后旧请求也无权获取；响应 no-store。',rules=['BR-07','BR-09'])
op('post','/items/{itemId}/clues','createClue','提交文字线索','线索','乙',['FR-23'],'Clue','ClueRequest',status='201',errors=['409'],desc='仅 PUBLISHED 的 LOST，不能为发布者，同一用户每物品最多一条线索；后续通过留言补充。线索内容对外隐藏。',rules=['BR-06','BR-09'])
op('get','/items/{itemId}/clues','listItemClues','查询该寻物信息收到的线索','线索','乙',['FR-25'],'CluePage',params=PAGE,desc='仅发布者，归档后仍可查，createdAt、id 降序；非发布者使用 /me/clues 查自己的线索。')
op('get','/clues/{clueId}','getClue','查询线索私有详情','线索','乙',['FR-23','FR-25'],'ClueDetail',desc='仅该线索提供者与寻物发布者；归档后保留，不产生已读副作用。')
op('post','/items/{itemId}/resolve','resolveLostItem','标记寻物信息已找回','线索','乙',['FR-26'],'ItemRecord','VersionRequest',errors=['409'],desc='仅发布者，类型 LOST，状态 PUBLISHED；无需已有线索。更新 ARCHIVED/FOUND，停收线索和留言；不改变任何招领信息。',rules=['BR-06','BR-09'])
op('get','/admin/items','listAdminItems','管理员审核列表','管理','丙',['FR-20'],'ItemRecordPage',params=PAGE+[q('status',ref('ItemStatus'))],desc='仅管理员，默认筛选 PENDING_REVIEW；createdAt、id 升序。超 24 小时高亮不纳入当前必须范围。')
op('get','/admin/items/{itemId}','getAdminItem','管理员审核详情','管理','丙',['FR-20'],'ItemRecord',desc='仅管理员；不附带其他用户凭证、留言、联系方式。')
op('post','/admin/items/{itemId}/approve','approveItem','审核通过','管理','丙',['FR-20'],'ItemRecord','VersionRequest',errors=['409'],desc='仅管理员，PENDING_REVIEW 转 PUBLISHED，publishedAt 更新为本次通过时间；写审核记录。',rules=['BR-04'])
op('post','/admin/items/{itemId}/return','returnItem','审核退回','管理','丙',['FR-20'],'ItemRecord','ReasonVersionRequest',errors=['409'],desc='仅管理员，PENDING_REVIEW 转 RETURNED，原因必填，发布者可查看。',rules=['BR-04'])
op('get','/admin/items/{itemId}/review-records','listReviewRecords','查询审核处置记录','管理','丙',['FR-20','FR-21'],'ReviewRecordPage',params=PAGE,desc='仅管理员；createdAt、id 降序。')
op('post','/admin/items/{itemId}/take-down','adminTakeDown','下架违规信息','管理','丙',['FR-21'],'ItemRecord','ReasonVersionRequest',errors=['409'],optional=True,desc='仅管理员，PUBLISHED 转 OFF_SHELF/VIOLATION；事务内 PENDING/CONFIRMED 申请转 CLOSED，撤回授权，停止留言并审计。归档记录不以此接口删除。',rules=['BR-04','BR-07','BR-09'])
op('post','/reports','createReport','提交举报','举报','丙',['FR-22','FR-27'],'Report','ReportRequest',status='201',optional=True,errors=['429'],desc='普通举报须针对当前可见的公共物品；关联会话举报只允许该会话参与者且目标属于该物品，即使已归档也可举报。线索和认领上下文最多一个。不接受任意凭证附件。',rules=['BR-10'])
op('get','/me/reports','listMyReports','查询我提交的举报','举报','丙',['FR-22'],'ReportPage',params=PAGE,optional=True,desc='只返回当前举报人的记录与处理结论，createdAt、id 降序。')
op('get','/admin/reports','listAdminReports','管理员举报列表','举报','丙',['FR-22'],'ReportPage',params=PAGE+[q('status',string(enum=['PENDING','RESOLVED']))],optional=True,desc='仅管理员，默认 PENDING；createdAt、id 升序。')
op('get','/admin/reports/{reportId}','getAdminReport','管理员举报详情','举报','丙',['FR-22'],'Report',optional=True,desc='仅管理员；私有会话通过 conversation 接口按关联关系读取。')
op('get','/admin/reports/{reportId}/conversation','getReportConversation','查看举报关联凭证和留言','举报','丙',['FR-19','FR-22','FR-24'],'ReportConversation',params=PAGE,optional=True,errors=['409'],desc='仅管理员且举报 PENDING；必须有合法关联上下文，无关联返回 404，举报结束返回 409 REPORT_STATE_CONFLICT。记录管理员、举报、上下文、时间；留言按 createdAt、id 升序。',rules=['BR-10'])
op('post','/admin/reports/{reportId}/resolve','resolveReport','处理举报','举报','丙',['FR-22'],'Report','ResolveReportRequest',optional=True,errors=['409'],desc='仅管理员，举报 PENDING。KEEP 仅写结论；TAKE_DOWN 调用统一违规下架规则；REJECT_PENDING_CLAIM 只处理关联 PENDING 申请。业务处置与举报 RESOLVED 同一事务；冲突时均不修改。',rules=['BR-10','BR-09'])

for typ in ['ItemRecord','Claim','Clue','PublicItem','Message','ReviewRecord','Report']:
    page(typ)

def build():
    paths={}
    for method,path,value,*_ in OPS:
        paths.setdefault(path,{})[method]=value
    spec={
      'openapi':'3.0.3',
      'info':{'title':'校园失物招领系统接口合同','version':'0.1.0-draft','description':'开发前合同草案，尚无实现或测试通过声明。D-01～D-04 为已确认产品选择，其余 BR 规则为本轮设计建议，待团队评审。路径服务器前缀 /api/v1。'},
      'servers':[{'url':'http://localhost:8080/api/v1','description':'本地开发'}],
      'security':[{'SessionAuth':[]}],
      'tags':[{'name':n} for n in ['认证','个人中心','发布与查找','认领','线索','联系方式','管理','举报']],
      'paths':paths,
      'components':{
        'securitySchemes':{'SessionAuth':{'type':'apiKey','in':'cookie','name':'JSESSIONID','description':'登录会话 Cookie；由浏览器管理，不是 Bearer/JWT。匿名会话不等于已认证身份。'}},
        'parameters':{'CsrfHeader':{'name':'X-CSRF-TOKEN','in':'header','required':True,'description':'所有写接口包括注册、登录必须携带；通过 GET /auth/csrf 获取，关联当前会话。','schema':{'type':'string','minLength':1}}},
        'schemas':S,
        'responses':{ 'Error'+c:{'description':ERROR_DESC[c]+'。可能错误码：'+', '.join(codes),'headers':{'Cache-Control':{'schema':{'type':'string','enum':['no-store']}}},'content':{'application/json':{'schema':ref('ErrorResponse')}}} for c,codes in ERRORS.items()}
      },
      'x-confirmed-decisions':{'D-01':'简化线索：文字、私有留言、标记找回','D-02':'联系方式默认隐藏，保留授权展示','D-03':'认领申请提交后开放留言','D-04':'认领确认后授权，交接完成后失效'},
      'x-design-status':'草案：接口字段、补充状态、限制和分工供团队评审；不是老师原文要求。',
    }
    return spec

def plain_type(value):
    if '$ref' in value:return value['$ref'].split('/')[-1]
    if value.get('type')=='array':return 'array<'+plain_type(value['items'])+'>'
    return value.get('type','object') + (' / null' if value.get('nullable') else '')

def render_catalog(spec):
    lines=['# 校园失物招领系统接口目录与字段','',
           '版本：0.1.0-draft。接口定义以同目录 openapi.yaml 为准；业务前置条件见《接口文档》。全部路径前缀为 `/api/v1`。下列分工是沿用既有六组功能并补充的建议，不代表三人已实际完成工作。','',
           '错误响应的 HTTP 状态码与对应业务错误码见《接口文档》。写请求均需 CSRF 令牌，登录要求另列；匿名公开读取没有用户会话要求。','']
    for tag in spec['tags']:
        lines+=['## '+tag['name'],'']
        for method,path,value,out,body,status in OPS:
            if value['tags'][0]!=tag['name']:continue
            lines+=['### '+value['operationId']+' '+value['summary'],'',f'`{method.upper()} /api/v1{path}`','',
                    f"负责人建议：{value['x-owner']}；需求：{', '.join(value['x-requirement-ids'])}；范围：{'选做' if value['x-scope']=='optional' else '第一版合同范围'}。",'',value['description'],'',
                    f"登录：{'可匿名；描述中的条件权限仍需校验' if not value['security'] else '必须登录'}。成功：HTTP {status}，{'图片二进制' if value['operationId']=='getImage' else out+'Response'}。",'']
            if body:lines += [f'请求体：`{body}`。','']
            ps=[p for p in value['parameters'] if '$ref' not in p]
            if ps:
                lines+=['| 参数 | 位置 | 类型 | 必填 | 说明 |','| --- | --- | --- | --- | --- |']
                for p in ps:lines.append(f"| {p['name']} | {p['in']} | {plain_type(p['schema'])} | {'是' if p['required'] else '否'} | {p.get('description','')} |")
                lines+=['']
            lines+=['错误 HTTP：'+', '.join(c for c in value['responses'] if c!=status)+'。','']
    lines+=['## 共享字段模型','','所有请求对象拒绝未知字段；除明确 nullable 的字段外不允许 null。required 为必填，响应字段同样遵守该约束。','']
    for name,schema in S.items():
        lines+=['### '+name,'',schema.get('description','') or '共享模型。','']
        if schema.get('type')!='object':
            lines += ['```json',json.dumps(schema,ensure_ascii=False,indent=2),'```',''];continue
        lines+=['| 字段 | 类型 | 必填 | 约束与说明 |','| --- | --- | --- | --- |']
        for key,val in schema.get('properties',{}).items():
            constraints={k:v for k,v in val.items() if k in ['enum','minLength','maxLength','minimum','maximum','maxItems','minItems','pattern','uniqueItems','format']}
            desc=val.get('description','')+' '+(json.dumps(constraints,ensure_ascii=False) if constraints else '')
            lines.append(f"| {key} | {plain_type(val)} | {'是' if key in schema.get('required',[]) else '否'} | {desc.replace('|', '&#124;')} |")
        if not schema.get('properties'):lines+=['| — | — | — | 空对象 |']
        lines+=['']
    (ROOT/'接口目录与字段.md').write_text('\n'.join(lines),encoding='utf-8')

def traceability():
    with (ROOT/'接口需求追溯.csv').open('w',encoding='utf-8-sig',newline='') as file:
        writer=csv.writer(file)
        writer.writerow(['需求编号','接口ID','方法','路径','建议负责人','范围','计划用例ID','实现文件','执行结果'])
        for method,path,value,*_ in OPS:
            for req in value['x-requirement-ids']:
                writer.writerow([req,value['operationId'],method.upper(),'/api/v1'+path,value['x-owner'],value['x-scope'],value['x-test-ids'][0],'待实现','未执行'])

def test_plan():
    cases=[]
    for method,path,value,out,body,status in OPS:
        cases.append([value['x-test-ids'][0],','.join(value['x-requirement-ids']),value['operationId'],
                      '正向合同场景：'+value['summary'],
                      '按接口描述准备有权主体、合法前置状态、有效会话与写请求 CSRF；请求体满足 '+(body or '无请求体')+'。',
                      f'执行 {method.upper()} /api/v1{path}，检查数据库副作用（如适用）。',
                      f'HTTP {status}；返回 '+('JPEG/PNG 图片' if value['operationId']=='getImage' else out+'Response')+'；数据归属、字段和副作用与接口描述一致。','未执行'])
    special=[
      ('AUTH-01','FR-02','logout,getMe','退出后旧 Cookie','同一账号已登录','退出后再次携带旧 Cookie GET /me','401 AUTH_REQUIRED'),
      ('AUTH-02','FR-01','register','验证码边界','准备错误、过期、已使用验证码及新学号','分别尝试注册；再用合法验证码提交','错误/过期/重复使用均失败且不建号，合法请求只创建一个账号'),
      ('AUTH-03','FR-01','register','身份与角色越权','普通匿名注册流程','伪造名册组合或增加 role=ADMIN','400，身份不匹配/未知字段拒绝，未创建管理员'),
      ('AUTH-04','SUP-01','createItem','CSRF 防护','持有有效登录 Cookie','缺失或错误 CSRF 令牌发布','403 CSRF_INVALID；没有写入'),
      ('ITEM-01','FR-07,FR-14','createItem,getPublicItem','审核前不可公开','存在待审核物品及其图片','匿名访问列表、猜测详情和图片 ID','列表无此项，详情和图片 404'),
      ('ITEM-02','FR-10','uploadImage','文件边界','登录用户有正确 CSRF','上传合法 2097152 字节 PNG、超 1 字节图片、伪装 JPG 的其他内容','边界合法图成功；超限 413；伪装格式 415'),
      ('ITEM-03','FR-10','createItem','图片归属与数量','甲乙各有自己的未绑定图片','甲引用乙图片；然后尝试绑定 4 张','拒绝，不能绑定他人图片或超过 3 张'),
      ('ITEM-04','FR-08','editItem','编辑回到待审','本人 PUBLISHED 且无认领/线索历史','合法编辑后匿名查询','物品 PENDING_REVIEW，公共不可见；有业务历史时编辑 409'),
      ('ITEM-05','FR-11,FR-12,FR-13','listPublicItems','组合筛选与翻页','多种类型/类别/日期及同时间数据','组合筛选、关键词含 %、连续翻页、查无结果','AND 条件、普通字符匹配、稳定排序；每页最多 10；空列表 total=0'),
      ('CLAIM-01','FR-15','createClaim','重复和自认领','有效招领物品','并发重复提交；发布人提交；对 LOST 提交','唯一一条申请；自认领拒绝；LOST 状态/类型冲突'),
      ('CLAIM-02','FR-16','confirmClaim','并发确认不同申请','同物品两个 PENDING 申请','并发确认两条，检查物品与申请','仅一条 CONFIRMED，另一条 CLOSED；不能出现双确认'),
      ('CLAIM-03','FR-17,FR-18','rejectClaim,completeClaim','非法状态跳转','PENDING 或终态申请','未确认就交接；无原因驳回；重复完成','分别状态冲突/参数错误/冲突，不能重复产生副作用'),
      ('CONTACT-01','FR-06','setMyContactPermission,getCounterpartyContact','确认前不可授权','申请 PENDING','授权手机号并查询对方资料','均 409 CONTACT_NOT_ACTIVE，不泄露数据'),
      ('CONTACT-02','FR-06','setMyContactPermission,getCounterpartyContact','字段与对象最小授权','申请 CONFIRMED，双方有效资料','仅授权手机号；对方查询；第三人猜 ID','对方仅收到 phone；email 缺省；第三人 404；公共详情无联系方式'),
      ('CONTACT-03','FR-06','setMyContactPermission,getCounterpartyContact','撤回与旧页面','已授权手机号并记住旧授权版本','用最新版本撤回，再用旧版本重放授权；对方查询','旧版本写 409 VERSION_CONFLICT；查询 403 CONTACT_NOT_AUTHORIZED'),
      ('CONTACT-04','FR-06,FR-18','completeClaim,getCounterpartyContact','交接完成后失效','双方已有有效授权','完成交接提交后再用旧 URL/Cookie 查询','409 CONTACT_NOT_ACTIVE；双方授权失效；响应 no-store'),
      ('CONTACT-05','FR-06,FR-18','setMyContactPermission,completeClaim','授权与完成并发','CONFIRMED 申请及有效版本','并发授权和完成，完成后再次查询','最终 COMPLETED，授权不能被迟到请求重新打开；后续读取失败'),
      ('CONTACT-06','FR-06','updateProfile,changeEmail','资料变更撤回','已授权手机号/邮箱','修改自己手机号或邮箱','对应字段所有旧授权撤回且版本递增，未获新授权不能读新值'),
      ('MSG-01','FR-19,FR-24','sendClaimMessage,sendClueMessage','提交后即可留言','刚创建申请或线索','双方发送；同物品其他申请人读取','双方成功；无关用户 404；认领无需先确认'),
      ('MSG-02','FR-19,FR-24','sendClaimMessage,sendClueMessage','终态留言只读','已驳回/完成/关闭流程','读取历史后尝试发送','历史可查；发送 409 CONVERSATION_CLOSED'),
      ('CLUE-01','FR-23','createClue','线索类型与唯一性','LOST 与 FOUND 各一条','对 FOUND 提交；自提交；并发同人重复提交','前两项失败；同人同 LOST 最多一条'),
      ('CLUE-02','FR-23,FR-26','createClue,resolveLostItem','找回与线索并发','PUBLISHED LOST','并发标记找回与提交；归档后再提交','事务顺序明确；归档后不能新增；历史线索保留'),
      ('ADMIN-01','FR-20','approveItem,returnItem','审核权限与原因','待审核物品','学生调用审核；管理员无原因退回；正常通过','学生 403，无原因 400，正常通过后才公开'),
      ('ADMIN-02','FR-19,FR-22,FR-24','getClaim,listClaimMessages,getReportConversation','举报限定管理员读权限','管理员、待处理关联举报及无关会话','直接读私有会话；通过举报读；处理后再读','直接路径 404；仅待处理关联上下文可读且有审计；处理后 409'),
      ('ADMIN-03','FR-21,FR-06','adminTakeDown,getCounterpartyContact','违规下架关闭关系','PUBLISHED FOUND，有 CONFIRMED 与有效授权','管理员带原因下架','物品隐藏、申请 CLOSED、授权失效、停止留言，审计完整'),
      ('REPORT-01','FR-22,FR-27','createReport,resolveReport','举报关联伪造与处置事务','物品、会话和待处理举报','提交他人会话或同时填两个上下文；对已确认申请执行 REJECT_PENDING_CLAIM','非法关联被拒绝；非法处置 409 且举报保持 PENDING，无部分写入'),
    ]
    for cid,req,ops,title,pre,steps,expected in special:
        cases.append(['IT-'+cid,req,ops,title,pre,steps,expected,'未执行'])
    with (ROOT/'接口验收计划.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.writer(f)
        writer.writerow(['用例ID','需求编号','接口ID','场景','前置条件','步骤','预期结果','执行状态'])
        writer.writerows(cases)

def validate(spec):
    validate_spec(spec)
    operations=[(m,p,v) for p,entry in spec['paths'].items() for m,v in entry.items()]
    ids=[v['operationId'] for _,_,v in operations]
    assert len(ids)==len(set(ids)), 'operationId 重复'
    refs=[]
    def walk(x):
        if isinstance(x,dict):
            if '$ref' in x:refs.append(x['$ref'])
            for v in x.values():walk(v)
        elif isinstance(x,list):
            for v in x:walk(v)
    walk(spec)
    for r in refs:
        assert r.startswith('#/'),r
        target=spec
        for token in r[2:].split('/'):target=target[token]
    for method,path,value in operations:
        assert value['x-owner'] and value['x-requirement-ids'] and value['x-test-ids']
        if method not in ['get','head','options']:
            assert {'$ref':'#/components/parameters/CsrfHeader'} in value['parameters']
        if path.startswith('/admin/'):
            assert value['security']==[{'SessionAuth':[]}]
        for status,response in value['responses'].items():
            if status.startswith('2'):
                assert 'Cache-Control' in response['headers']
    public=S['PublicItem']['properties']
    assert not {'phone','email','realName','studentId','claims','clues'} & set(public)
    assert set(S['ContactPermissionRequest']['properties'])=={'expectedVersion','phoneVisible','emailVisible'}
    required={f'FR-{i:02d}' for i in range(1,29)}
    covered={r for _,_,v in operations for r in v['x-requirement-ids']}
    assert required <= covered, required-covered
    samples={
      'ContactPermissionRequest':{'expectedVersion':0,'phoneVisible':True,'emailVisible':False},
      'ClaimRequest':{'description':'黑色水杯的杯底有一道明显划痕'},
      'ClueRequest':{'content':'我在图书馆三楼服务台看到类似的水杯'},
      'CreateItemRequest':{'type':'FOUND','category':'DAILY','location':'图书馆三楼','occurredDate':'2026-11-02','title':'捡到黑色水杯','description':'黑色保温水杯，已经交到三楼服务台','imageIds':[]},
      'ErrorResponse':{'code':'CONTACT_NOT_ACTIVE','message':'当前认领阶段不允许查看联系方式','data':None,'requestId':'req-demo','errors':[]},
    }
    for name,data in samples.items():OAS30Validator(S[name]).validate(data)
    for name,data in [('ContactPermissionRequest',{'expectedVersion':0,'phoneVisible':True,'emailVisible':False,'recipientId':'999'}),('ClaimRequest',{'description':'太短'}),('ClueRequest',{'content':'有效线索说明内容不少于十个字','imageIds':['1']})]:
        assert list(OAS30Validator(S[name]).iter_errors(data)), '非法示例被接受 '+name
    for _,_,value in operations:
        for media in value.get('requestBody',{}).get('content',{}).values():
            if 'example' in media:
                schema=S[media['schema']['$ref'].split('/')[-1]]
                OAS30Validator(schema).validate(media['example'])
    with (ROOT/'接口验收计划.csv').open(encoding='utf-8-sig',newline='') as file:
        tests=list(csv.DictReader(file))
    test_ids={t['用例ID'] for t in tests}
    assert len(tests)==len(test_ids), '用例编号重复'
    assert all(t['执行状态']=='未执行' for t in tests)
    for _,_,value in operations:
        assert set(value['x-test-ids']) <= test_ids
    with (ROOT/'接口需求追溯.csv').open(encoding='utf-8-sig',newline='') as file:
        for row in csv.DictReader(file):
            assert row['接口ID'] in ids and row['计划用例ID'] in test_ids
    for md in ROOT.glob('*.md'):
        for pair in re.findall(r'\]\(<([^>]+)>\)|\]\(([^)<>]+)\)',md.read_text(encoding='utf-8')):
            target = next(value for value in pair if value)
            if '://' not in target and not target.startswith('#'):
                assert (md.parent/target.split('#')[0]).exists(), f'文档链接缺失 {md.name}: {target}'
    report={'status':'passed','contractVersion':spec['info']['version'],'operations':len(operations),'paths':len(spec['paths']),'schemas':len(spec['components']['schemas']),'requirementCoverage':sorted(covered),'checks':['OpenAPI 3.0.3 结构通过官方模式校验','全部本地引用可解析','operationId 唯一','每个写接口声明 CSRF 请求头','管理员接口声明 Session 认证','成功响应声明缓存控制','公开物品模型不含私有联系方式','授权输入无任意用户编号','FR-01 至 FR-28 均有关联接口','5 个合法示例通过模式校验，3 个非法示例被拒绝'],'limitations':'仅校验文档结构、模型和静态合同一致性；未启动后端、未连接数据库、未证明运行时权限、事务或性能正确。'}
    report['plannedTestCases']=len(tests)
    report['optionalOperations']=sum(v['x-scope']=='optional' for _,_,v in operations)
    report['checks']+=['OpenAPI 内嵌请求示例均符合相应模型','计划用例编号唯一且全部标记未执行','接口追溯引用有效接口与有效计划用例','文档相对链接目标存在']
    (ROOT/'接口文档校验结果.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))

if __name__=='__main__':
    if '--validate-only' in sys.argv:
        spec=yaml.safe_load((ROOT/'openapi.yaml').read_text(encoding='utf-8'))
        S=spec['components']['schemas']
        validate(spec)
    else:
        spec=build()
        (ROOT/'openapi.yaml').write_text(yaml.safe_dump(spec,allow_unicode=True,sort_keys=False,width=110),encoding='utf-8')
        render_catalog(spec)
        traceability()
        test_plan()
        validate(spec)
