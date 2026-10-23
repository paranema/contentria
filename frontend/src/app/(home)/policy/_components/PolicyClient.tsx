'use client';

import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';

export default function PolicyClient() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const initialTab = searchParams.get('tab') as 'privacy' | 'terms' | null;
  const [activeTab, setActiveTab] = useState<'privacy' | 'terms'>(
    initialTab === 'terms' ? 'terms' : 'privacy'
  );

  useEffect(() => {
    if (initialTab === 'terms') {
      setActiveTab('terms');
    } else {
      setActiveTab('privacy');
    }
  }, [initialTab]);

  const handleTabClick = (tab: 'privacy' | 'terms') => {
    setActiveTab(tab);
    const params = new URLSearchParams(searchParams.toString());
    params.set('tab', tab);
    router.replace(`${pathname}?${params.toString()}`, { scroll: false });
  };

  return (
    <div className="flex min-h-screen flex-col">
      <section className="bg-gradient-to-br from-indigo-50 via-white to-white py-12" />

      <div className="container mx-auto max-w-4xl px-6 py-12">
        <div className="mb-8 flex space-x-4 border-b">
          <button
            onClick={() => handleTabClick('privacy')}
            className={`border-b-2 px-4 py-2 text-sm font-semibold ${
              activeTab === 'privacy'
                ? 'border-indigo-600 text-indigo-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            개인정보처리방침
          </button>
          <button
            onClick={() => handleTabClick('terms')}
            className={`border-b-2 px-4 py-2 text-sm font-semibold ${
              activeTab === 'terms'
                ? 'border-indigo-600 text-indigo-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            서비스 이용약관
          </button>
        </div>

        <div className="prose prose-indigo mx-auto">
          {activeTab === 'privacy' ? (
            <>
              <h1 className="text-3xl font-bold text-gray-900">개인정보처리방침</h1>
              <p className="text-gray-600">최종 수정일: 2026년 5월 3일</p>

              <p className="mt-4 text-gray-700">
                Contentria(이하 &quot;회사&quot;)는 「개인정보 보호법」 제30조에 따라 정보주체의
                개인정보를 보호하고 이와 관련한 고충을 신속하고 원활하게 처리할 수 있도록 다음과
                같이 개인정보 처리방침을 수립·공개합니다.
              </p>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">1. 개인정보의 처리 목적</h2>
                <p className="mt-4 text-gray-700">
                  회사는 다음의 목적을 위하여 개인정보를 처리합니다. 처리하고 있는 개인정보는 다음의
                  목적 이외의 용도로는 이용되지 않으며, 이용 목적이 변경되는 경우에는 「개인정보
                  보호법」 제18조에 따라 별도의 동의를 받는 등 필요한 조치를 이행할 예정입니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>
                    회원 가입 및 관리: 회원 가입 의사 확인, 회원제 서비스 제공에 따른 본인
                    식별·인증, 회원자격 유지·관리, 서비스 부정이용 방지, 각종 고지·통지, 고충처리
                  </li>
                  <li>
                    재화 또는 서비스 제공: 블로그 콘텐츠 작성·게시·관리 기능 제공, 맞춤 콘텐츠 제공,
                    서비스 이용 통계 산출
                  </li>
                  <li>
                    서비스 개선 및 보안: 서비스 이용 기록 분석, 비정상 접근 탐지, 자동 가입·접근
                    차단(reCAPTCHA)
                  </li>
                </ul>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">
                  2. 처리하는 개인정보의 항목
                </h2>
                <p className="mt-4 text-gray-700">
                  회사는 다음의 개인정보 항목을 처리하고 있습니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>
                    회원가입 및 관리(필수): 이메일 주소, 비밀번호(자체 가입 시), Google 계정
                    식별자(소셜 로그인 시)
                  </li>
                  <li>회원가입 및 관리(선택): 닉네임, 프로필 이미지</li>
                  <li>
                    서비스 이용 과정에서 자동 수집: 접속 IP 주소, User-Agent, 접속 일시, Referer,
                    방문한 페이지 기록
                  </li>
                </ul>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">
                  3. 개인정보의 처리 및 보유 기간
                </h2>
                <p className="mt-4 text-gray-700">
                  회사는 법령에 따른 개인정보 보유·이용기간 또는 정보주체로부터 개인정보를 수집 시에
                  동의받은 개인정보 보유·이용기간 내에서 개인정보를 처리·보유합니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>
                    회원 가입 및 관리: 회원 탈퇴 시까지 (단, 관계 법령 위반에 따른 수사·조사 등이
                    진행 중인 경우에는 해당 수사·조사 종료 시까지)
                  </li>
                  <li>서비스 이용 기록(접속 로그): 수집일로부터 3개월</li>
                  <li>「통신비밀보호법」에 따른 통신사실확인자료: 수집일로부터 3개월</li>
                </ul>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">4. 개인정보의 제3자 제공</h2>
                <p className="mt-4 text-gray-700">
                  회사는 정보주체의 개인정보를 제1조에서 명시한 범위 내에서 처리하며, 정보주체의
                  사전 동의 없이는 본래의 목적 범위를 초과하여 처리하거나 제3자에게 제공하지
                  않습니다. 다만, 다음의 경우에는 예외로 합니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>정보주체로부터 별도의 동의를 받은 경우</li>
                  <li>법률에 특별한 규정이 있거나 법령상 의무를 준수하기 위하여 불가피한 경우</li>
                  <li>
                    명백히 정보주체 또는 제3자의 급박한 생명, 신체, 재산의 이익을 위하여 필요하다고
                    인정되는 경우
                  </li>
                </ul>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">5. 개인정보 처리의 위탁</h2>
                <p className="mt-4 text-gray-700">
                  회사는 원활한 서비스 제공을 위하여 다음과 같이 개인정보 처리 업무를 외부에
                  위탁하고 있습니다.
                </p>
                <div className="mt-4 overflow-x-auto">
                  <table>
                    <thead>
                      <tr>
                        <th>수탁자</th>
                        <th>위탁 업무</th>
                        <th>국가</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>Mailgun Technologies, Inc.</td>
                        <td>이메일 발송 및 전송 결과 처리</td>
                        <td>미국</td>
                      </tr>
                      <tr>
                        <td>Google LLC</td>
                        <td>소셜 로그인(OAuth) 인증, reCAPTCHA를 통한 자동 가입·접근 차단</td>
                        <td>미국</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <p className="mt-4 text-gray-700">
                  회사는 위탁계약 체결 시 「개인정보 보호법」 제26조에 따라 위탁업무 수행 목적 외
                  개인정보 처리 금지, 기술적·관리적 보호조치, 재위탁 제한, 수탁자에 대한 관리·감독,
                  손해배상 등 책임에 관한 사항을 계약서 등 문서에 명시하고, 수탁자가 개인정보를
                  안전하게 처리하는지를 감독하고 있습니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">
                  6. 정보주체의 권리·의무 및 행사 방법
                </h2>
                <p className="mt-4 text-gray-700">
                  정보주체는 회사에 대해 언제든지 다음 각 호의 개인정보 보호 관련 권리를 행사할 수
                  있습니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>개인정보 열람 요구</li>
                  <li>개인정보 정정·삭제 요구</li>
                  <li>개인정보 처리정지 요구</li>
                  <li>개인정보 처리에 대한 동의 철회</li>
                </ul>
                <p className="mt-4 text-gray-700">
                  권리 행사는 회사에 대해 서면, 이메일 등을 통하여 하실 수 있으며 회사는 이에 대해
                  지체 없이 조치하겠습니다. 정보주체가 개인정보의 오류 등에 대한 정정 또는 삭제를
                  요구한 경우에는 회사는 정정 또는 삭제를 완료할 때까지 해당 개인정보를 이용하거나
                  제공하지 않습니다.
                </p>
                <p className="mt-4 text-gray-700">
                  권리 행사는 정보주체의 법정대리인이나 위임을 받은 자 등 대리인을 통하여 하실 수
                  있으며, 이 경우 「개인정보 처리 방법에 관한 고시」 별지 제11호 서식에 따른
                  위임장을 제출하셔야 합니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">7. 개인정보의 파기</h2>
                <p className="mt-4 text-gray-700">
                  회사는 개인정보 보유기간의 경과, 처리목적 달성 등 개인정보가 불필요하게 되었을
                  때에는 지체 없이 해당 개인정보를 파기합니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>
                    파기 절차: 이용자가 입력한 정보는 목적 달성 후 별도의 DB로 옮겨져 내부 방침 및
                    기타 관련 법령에 따라 일정 기간 저장된 후 혹은 즉시 파기됩니다.
                  </li>
                  <li>
                    파기 방법: 전자적 파일 형태의 정보는 기록을 재생할 수 없는 기술적 방법을
                    사용하여 삭제하며, 종이 문서에 기록·저장된 개인정보는 분쇄기로 분쇄하거나
                    소각하여 파기합니다.
                  </li>
                </ul>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">
                  8. 개인정보의 안전성 확보조치
                </h2>
                <p className="mt-4 text-gray-700">
                  회사는 「개인정보 보호법」 제29조에 따라 다음과 같이 안전성 확보에 필요한
                  기술적·관리적 및 물리적 조치를 하고 있습니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>관리적 조치: 내부관리계획 수립·시행, 개인정보 취급자 최소화 및 교육</li>
                  <li>
                    기술적 조치: 비밀번호 단방향 암호화 저장, 개인정보 처리 시스템 접근권한 관리,
                    보안 프로그램 설치·운영
                  </li>
                  <li>전송 구간 암호화: HTTPS(TLS)를 이용한 개인정보 송·수신 암호화</li>
                  <li>물리적 조치: 서버 등 개인정보가 보관된 장소에 대한 접근 통제</li>
                </ul>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">
                  9. 개인정보 자동 수집 장치(쿠키)의 설치·운영 및 거부
                </h2>
                <p className="mt-4 text-gray-700">
                  회사는 이용자에게 서비스를 원활하게 제공하기 위하여 이용자의 정보를 저장하고
                  수시로 불러오는 쿠키(cookie)를 사용합니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>
                    쿠키의 사용 목적: 로그인 상태 유지를 위한 인증 토큰(access token, refresh token)
                    저장
                  </li>
                  <li>회사는 광고·분석 목적의 쿠키를 사용하지 않습니다.</li>
                  <li>
                    쿠키 거부 방법: 웹 브라우저 설정의 옵션을 통해 쿠키 저장을 거부할 수 있습니다.
                    다만 쿠키 저장을 거부할 경우 로그인이 필요한 서비스 이용에 어려움이 있을 수
                    있습니다.
                  </li>
                </ul>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">10. 개인정보 보호책임자</h2>
                <p className="mt-4 text-gray-700">
                  회사는 개인정보 처리에 관한 업무를 총괄해서 책임지고, 개인정보 처리와 관련한
                  정보주체의 불만처리 및 피해구제 등을 위하여 아래와 같이 개인정보 보호책임자를
                  지정하고 있습니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>개인정보 보호책임자: Contentria 운영팀</li>
                  <li>연락처: help@contentria.com</li>
                </ul>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">11. 권익침해 구제방법</h2>
                <p className="mt-4 text-gray-700">
                  정보주체는 개인정보 침해로 인한 구제를 받기 위하여 개인정보분쟁조정위원회,
                  한국인터넷진흥원 개인정보침해신고센터 등에 분쟁해결이나 상담 등을 신청할 수
                  있습니다. 이 밖에 기타 개인정보 침해의 신고·상담에 대하여는 아래 기관에 문의하시기
                  바랍니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>개인정보분쟁조정위원회: (국번없이) 1833-6972 / www.kopico.go.kr</li>
                  <li>개인정보침해신고센터: (국번없이) 118 / privacy.kisa.or.kr</li>
                  <li>대검찰청: (국번없이) 1301 / www.spo.go.kr</li>
                  <li>경찰청: (국번없이) 182 / ecrm.cyber.go.kr</li>
                </ul>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">12. 개인정보 처리방침 변경</h2>
                <p className="mt-4 text-gray-700">
                  이 개인정보 처리방침은 시행일로부터 적용되며, 법령 및 방침에 따른 변경내용의 추가,
                  삭제 및 정정이 있는 경우에는 변경사항의 시행 7일 전부터 공지사항을 통하여 고지할
                  것입니다.
                </p>
              </section>
            </>
          ) : (
            <>
              <h1 className="text-3xl font-bold text-gray-900">서비스 이용약관</h1>
              <p className="text-gray-600">최종 수정일: 2026년 5월 3일</p>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">제1조 (목적)</h2>
                <p className="mt-4 text-gray-700">
                  이 약관은 Contentria(이하 &quot;회사&quot;)가 제공하는 블로그 서비스(이하
                  &quot;서비스&quot;)의 이용과 관련하여 회사와 회원 간의 권리, 의무 및 책임사항,
                  기타 필요한 사항을 규정함을 목적으로 합니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">제2조 (정의)</h2>
                <p className="mt-4 text-gray-700">
                  이 약관에서 사용하는 용어의 정의는 다음과 같습니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>
                    &quot;서비스&quot;라 함은 회사가 제공하는 블로그 콘텐츠 작성·게시·열람 서비스를
                    말합니다.
                  </li>
                  <li>
                    &quot;회원&quot;이라 함은 서비스에 접속하여 이 약관에 따라 회사와 이용계약을
                    체결하고 회사가 제공하는 서비스를 이용하는 자를 말합니다.
                  </li>
                  <li>
                    &quot;아이디(ID)&quot;라 함은 회원의 식별과 서비스 이용을 위하여 회원이 등록한
                    이메일 주소 또는 소셜 로그인 식별자를 말합니다.
                  </li>
                  <li>
                    &quot;비밀번호&quot;라 함은 회원의 본인 확인 및 정보 보호를 위해 회원 자신이
                    정한 문자·숫자의 조합을 말합니다.
                  </li>
                  <li>
                    &quot;게시물&quot;이라 함은 회원이 서비스를 이용하면서 작성·게시한 글, 이미지,
                    코드, 링크 등 일체의 콘텐츠를 말합니다.
                  </li>
                </ul>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">제3조 (약관의 게시와 개정)</h2>
                <p className="mt-4 text-gray-700">
                  회사는 이 약관의 내용을 회원이 쉽게 알 수 있도록 서비스 초기 화면 또는
                  푸터(footer)에 게시합니다. 회사는 「약관의 규제에 관한 법률」, 「정보통신망
                  이용촉진 및 정보보호 등에 관한 법률」 등 관련법을 위배하지 않는 범위에서 이 약관을
                  개정할 수 있으며, 약관이 개정되는 경우 적용일자 및 개정사유를 명시하여 적용일자
                  7일 전부터 공지합니다. 다만 회원에게 불리한 개정의 경우 적용일자 30일 전부터
                  공지합니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">제4조 (이용계약 체결)</h2>
                <p className="mt-4 text-gray-700">
                  이용계약은 회원이 되고자 하는 자(이하 &quot;가입신청자&quot;)가 약관의 내용에
                  대하여 동의한 다음 회원가입을 신청하고, 회사가 이러한 신청에 대하여 승낙함으로써
                  체결됩니다. 가입신청자가 만 14세 미만인 경우 서비스 이용이 제한될 수 있습니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">
                  제5조 (서비스의 제공 및 변경)
                </h2>
                <p className="mt-4 text-gray-700">
                  회사는 회원에게 아래와 같은 서비스를 제공합니다.
                </p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>블로그 개설 및 운영 서비스</li>
                  <li>콘텐츠 작성·편집·게시·관리 서비스</li>
                  <li>
                    기타 회사가 추가 개발하거나 다른 회사와의 제휴계약 등을 통해 회원에게 제공하는
                    일체의 서비스
                  </li>
                </ul>
                <p className="mt-4 text-gray-700">
                  회사가 제공하는 서비스는 현재 무료로 제공되며, 향후 일부 서비스가 유료로 전환될 수
                  있습니다. 이 경우 회사는 변경 내용 및 적용일자를 사전 공지합니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">제6조 (서비스의 중단)</h2>
                <p className="mt-4 text-gray-700">
                  회사는 컴퓨터 등 정보통신설비의 보수점검·교체 및 고장, 통신의 두절 등의 사유가
                  발생한 경우에는 서비스의 제공을 일시적으로 중단할 수 있습니다. 이 경우 회사는
                  사전에 공지하며, 사전 공지가 곤란한 부득이한 사정이 있는 경우 사후에 통지할 수
                  있습니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">제7조 (회원의 의무)</h2>
                <p className="mt-4 text-gray-700">회원은 다음 행위를 하여서는 안 됩니다.</p>
                <ul className="mt-4 list-disc pl-6 text-gray-700">
                  <li>신청 또는 변경 시 허위 내용의 등록</li>
                  <li>타인의 정보 도용</li>
                  <li>회사가 게시한 정보의 변경</li>
                  <li>회사가 정한 정보 이외의 정보(컴퓨터 프로그램 등)의 송신 또는 게시</li>
                  <li>회사 또는 다른 회원의 저작권 등 지적재산권에 대한 침해</li>
                  <li>회사 또는 다른 회원의 명예를 손상시키거나 업무를 방해하는 행위</li>
                  <li>
                    외설 또는 폭력적인 메시지·화상·음성, 기타 공서양속에 반하는 정보를 서비스에 공개
                    또는 게시하는 행위
                  </li>
                  <li>
                    회사의 동의 없이 서비스를 영리 목적으로 이용하는 행위(스팸 발송, 무단 광고 등)
                  </li>
                  <li>
                    자동화된 수단으로 비정상적으로 서비스를 이용하거나 시스템에 부하를 발생시키는
                    행위
                  </li>
                  <li>기타 관련 법령을 위반하거나 부당한 행위</li>
                </ul>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">제8조 (회사의 의무)</h2>
                <p className="mt-4 text-gray-700">
                  회사는 관련법과 이 약관이 금지하거나 미풍양속에 반하는 행위를 하지 않으며,
                  계속적이고 안정적으로 서비스를 제공하기 위하여 최선을 다하여 노력합니다. 회사는
                  회원이 안전하게 서비스를 이용할 수 있도록 개인정보 보호를 위한 보안 시스템을
                  구축·운영합니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">제9조 (게시물의 저작권)</h2>
                <p className="mt-4 text-gray-700">
                  회원이 서비스 내에 작성·게시한 게시물의 저작권은 해당 게시물의 저작자인 회원에게
                  귀속됩니다.
                </p>
                <p className="mt-4 text-gray-700">
                  회원은 회사가 게시물을 서비스의 운영·전시·전송·배포·홍보 목적으로 합리적인 범위
                  내에서 무상으로 사용할 수 있도록 사용권을 부여합니다. 이 사용권은 회원이 서비스를
                  이용하는 동안 유효하며, 회원 탈퇴 또는 게시물 삭제 시 즉시 종료됩니다(다만
                  검색엔진 등 외부 캐시·인덱스로 인한 잔존은 회사의 통제 범위를 벗어납니다).
                </p>
                <p className="mt-4 text-gray-700">
                  회원은 자신이 게시한 게시물이 제3자의 저작권 등 지적재산권을 침해하지 않을 것을
                  보증하며, 침해로 인한 분쟁이 발생한 경우 회원이 직접 자신의 비용과 책임으로 분쟁을
                  해결하여야 합니다.
                </p>
                <p className="mt-4 text-gray-700">
                  회사는 회원의 게시물이 관련 법령 또는 이 약관에 위반된다고 판단되는 경우 사전 통지
                  없이 해당 게시물을 삭제·이동·접근 차단할 수 있습니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">제10조 (개인정보보호)</h2>
                <p className="mt-4 text-gray-700">
                  회사는 서비스를 제공하기 위하여 관련 법령의 규정에 따라 회원으로부터 필요한
                  개인정보를 수집합니다. 회사는 수집된 개인정보를 별도의 「개인정보처리방침」에 따라
                  적절히 관리·보호합니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">
                  제11조 (계약 해지 및 회원 탈퇴)
                </h2>
                <p className="mt-4 text-gray-700">
                  회원은 언제든지 서비스 내 회원 탈퇴 기능을 통해 이용계약을 해지할 수 있습니다.
                  회원이 탈퇴할 경우, 관련 법령 및 개인정보처리방침에 따라 보관이 필요한 정보를
                  제외한 회원의 개인정보 및 게시물은 즉시 또는 일정 기간 후 파기됩니다.
                </p>
                <p className="mt-4 text-gray-700">
                  회사는 회원이 이 약관 또는 관련 법령을 위반한 경우 사전 통지 후 이용계약을 해지할
                  수 있으며, 위반의 정도가 중대하거나 긴급한 경우에는 사전 통지 없이 해지할 수
                  있습니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">제12조 (책임제한)</h2>
                <p className="mt-4 text-gray-700">
                  회사는 천재지변 또는 이에 준하는 불가항력으로 인하여 서비스를 제공할 수 없는
                  경우에는 서비스 제공에 관한 책임이 면제됩니다. 회사는 회원의 귀책사유로 인한
                  서비스 이용의 장애에 대하여는 책임을 지지 않으며, 회원 상호 간 또는 회원과 제3자
                  간 서비스를 매개로 발생한 분쟁에 대하여 개입할 의무가 없으며 이로 인한 손해를
                  배상할 책임이 없습니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">제13조 (분쟁해결)</h2>
                <p className="mt-4 text-gray-700">
                  회사는 회원으로부터 제출되는 불만 처리 및 피해 구제 요청을 신속하게 처리하기
                  위하여 필요한 인력 및 시스템을 구비하고 있습니다. 회사는 회원으로부터 제출되는
                  불만사항 및 의견을 최대한 신속하게 처리합니다. 다만, 신속한 처리가 곤란한 경우에는
                  회원에게 그 사유와 처리 일정을 통보합니다.
                </p>
              </section>

              <section className="mt-8">
                <h2 className="text-2xl font-semibold text-gray-900">
                  제14조 (준거법 및 관할법원)
                </h2>
                <p className="mt-4 text-gray-700">
                  이 약관의 해석 및 회사와 회원 간의 분쟁에 대하여는 대한민국의 법을 적용하며, 본
                  분쟁으로 인한 소송은 「민사소송법」상의 관할을 가지는 대한민국의 법원에
                  제기합니다.
                </p>
              </section>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
