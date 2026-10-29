import { Check, Copy, HardDrive, ShieldAlert, Wrench } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useMediaQuery } from "@/hooks/use-media-query";
import { gsap, motion, useGSAP } from "@/lib/gsap";
import { drawAlong } from "@/lib/light-along";
import { links } from "@/lib/links";

const TYPE_DELAY_MS = 14;
const STATUS_FOR_MS = 2200;

const STEPS = [
  {
    title: "Download a build.",
    body: (
      <>
        <a
          href={links.releases}
          className="-my-3 inline-block py-3 text-foreground underline underline-offset-4 hover:text-primary"
        >
          Pick the newest release JAR
        </a>{" "}
        and rename it <code className="font-mono text-[.9em]">kindling.jar</code>.
      </>
    ),
  },
  {
    title: "Start it with Java 25.",
    body: "Like any Leaf or Paper JAR. Accept the EULA on the first run, then restart.",
  },
  {
    title: "Prepare the world.",
    body: "Optionally pregenerate the area your players use. Their chunks keep priority.",
  },
];

const COMMANDS = [
  {
    id: "start",
    label: "Start",
    prompt: "$",
    code: "java -Xms3G -Xmx3G -XX:+UseCompactObjectHeaders -jar kindling.jar --nogui",
    hint: "From Leaf, your configuration carries over unchanged; from Paper, Leaf's own files are written fresh. Size -Xmx to your server: with the benchmark's 8 players exploring new terrain, 2 GB froze for 0.7 s every 15 to 20 seconds and 3 GB far less often.",
  },
  {
    id: "pregen",
    label: "Pregenerate",
    prompt: ">",
    code: "/pregen start world 5000",
    hint: "In the console or as an operator. /pregen status shows progress; a restart resumes where it stopped.",
  },
  {
    id: "leaf",
    label: "From Leaf",
    prompt: "",
    code: [
      "async:",
      "  async-entity-tracker:",
      "    enabled: true",
      "  async-playerdata-save:",
      "    enabled: true",
      "performance:",
      "  sleeping-block-entity: true",
    ].join("\n"),
    hint: "Kindling's defaults apply when config/leaf-global.yml is written fresh. An existing file keeps its values, so set these yourself.",
  },
  {
    id: "velocity",
    label: "Behind Velocity",
    prompt: "",
    code: [
      "proxies:",
      "  velocity:",
      "    enabled: true",
      "    online-mode: true",
      "    secret: your-forwarding-secret",
    ].join("\n"),
    hint: "Paper's proxy support is unchanged: set this in config/paper-global.yml on every backend, as on Paper.",
  },
];

const FACTS = [
  { icon: Wrench, text: "Needs Java 25 and runs Minecraft 26.3." },
  {
    icon: HardDrive,
    text: "Pregenerating a 5 000-block radius takes about 5 GB of disk; 10 000 blocks about 21 GB.",
  },
  {
    icon: ShieldAlert,
    text: "Every build is a pre-release, and Kindling runs on one production server so far. Back up your worlds before switching.",
  },
];

export function GetStarted() {
  const root = useRef<HTMLElement>(null);

  useGSAP(
    () => {
      gsap.matchMedia().add(motion.allowed, () => drawAlong("[data-steps]"));
    },
    { scope: root },
  );

  return (
    <section
      ref={root}
      id="start"
      aria-labelledby="start-title"
      className="relative isolate overflow-clip py-24 md:py-32"
    >
      <div className="wrap">
        <div className="mx-auto max-w-3xl text-center">
          <h2 id="start-title" data-reveal className="text-[clamp(40px,5.2vw,72px)] leading-[1.04]">
            Light it up.
          </h2>
          <p data-reveal className="mt-5 text-base text-[#d6ddd3] md:text-lg">
            Three steps from the download to a prepared world.
          </p>
        </div>
        <ol
          data-steps
          className="relative m-0 mt-14 grid list-none gap-10 p-0 [--draw:1] md:grid-cols-3 md:gap-8 md:before:absolute md:before:top-[21px] md:before:right-[16%] md:before:left-[16%] md:before:h-0.5 md:before:bg-border md:after:absolute md:after:top-[21px] md:after:right-[16%] md:after:left-[16%] md:after:h-0.5 md:after:origin-left md:after:scale-x-(--draw) md:after:bg-linear-to-r md:after:from-primary md:after:to-ember-deep"
        >
          {STEPS.map((step, index) => (
            <li
              key={step.title}
              data-reveal
              className="relative flex flex-col items-center gap-2 text-center"
            >
              <span
                aria-hidden
                className="z-10 mb-2 grid size-11 place-items-center border-2 border-t-white/30 border-r-black/60 border-b-black/60 border-l-white/30 bg-primary font-display text-lg text-primary-foreground"
              >
                {index + 1}
              </span>
              <strong className="text-[17px]">{step.title}</strong>
              <p className="max-w-[24em] text-[15px] text-muted-foreground">{step.body}</p>
            </li>
          ))}
        </ol>
        <div data-reveal className="mx-auto mt-14 max-w-3xl">
          <CommandPanel />
        </div>
        <ul
          aria-label="Before you switch"
          className="m-0 mt-12 grid list-none gap-x-8 gap-y-5 border-t border-white/10 p-0 pt-8 md:grid-cols-3"
        >
          {FACTS.map((fact) => (
            <li
              key={fact.text}
              data-reveal
              className="flex items-start gap-3 text-sm leading-[22px] text-[#d6ddd3]"
            >
              <fact.icon aria-hidden className="mt-0.5 size-4 shrink-0 text-primary" />
              {fact.text}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

/** Types the command out once it is on screen; screen readers and reduced motion get the whole text at once. */
function TypedCode({ code, prompt }: { code: string; prompt: string }) {
  const reducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
  const [shown, setShown] = useState(reducedMotion ? code.length : 0);
  const element = useRef<HTMLPreElement>(null);

  useEffect(() => {
    if (reducedMotion) {
      setShown(code.length);
      return;
    }
    setShown(0);
    let timer = 0;
    const observer = new IntersectionObserver((entries) => {
      if (!entries.at(-1)?.isIntersecting) return;
      observer.disconnect();
      let typed = 0;
      timer = window.setInterval(() => {
        typed += 1;
        setShown(typed);
        if (typed >= code.length) window.clearInterval(timer);
      }, TYPE_DELAY_MS);
    });
    if (element.current) observer.observe(element.current);
    return () => {
      observer.disconnect();
      window.clearInterval(timer);
    };
  }, [code, reducedMotion]);

  return (
    <pre
      ref={element}
      className="m-0 font-mono text-[13px] leading-6 break-normal whitespace-pre-wrap text-foreground md:text-sm"
    >
      <span className="sr-only">{code}</span>
      <code aria-hidden>
        {prompt && <span className="mr-2 text-primary select-none">{prompt}</span>}
        {code.slice(0, shown)}
        <span className="ml-0.5 inline-block h-[1.1em] w-[0.55em] translate-y-[0.2em] animate-pulse bg-primary/80 motion-reduce:hidden" />
      </code>
    </pre>
  );
}

function CommandPanel() {
  const [active, setActive] = useState("start");
  const [status, setStatus] = useState<{ id: string; copied: boolean }>();
  const activeCommand = COMMANDS.find((command) => command.id === active);
  const copied = status?.id === active && status.copied;

  useEffect(() => {
    if (!status) return;
    const timer = window.setTimeout(() => setStatus(undefined), STATUS_FOR_MS);
    return () => window.clearTimeout(timer);
  }, [status]);

  const copyActive = async () => {
    if (!activeCommand) return;
    setStatus(undefined);
    try {
      await navigator.clipboard.writeText(activeCommand.code);
      setStatus({ id: activeCommand.id, copied: true });
    } catch {
      setStatus({ id: activeCommand.id, copied: false });
    }
  };

  return (
    <Tabs
      value={active}
      onValueChange={(value) => {
        setActive(value);
        setStatus(undefined);
      }}
      className="gap-0 overflow-hidden rounded-xl border bg-card"
    >
      <TabsList
        variant="line"
        aria-label="Server setup"
        className="w-full flex-wrap justify-start gap-x-6 gap-y-0 rounded-none border-b px-6 py-1.5 group-data-[orientation=horizontal]/tabs:h-auto"
      >
        {COMMANDS.map((command) => (
          <TabsTrigger
            key={command.id}
            value={command.id}
            className="h-11 flex-none px-0 text-[13px] font-bold after:bg-primary"
          >
            {command.label}
          </TabsTrigger>
        ))}
      </TabsList>
      {COMMANDS.map((command) => (
        <TabsContent
          key={command.id}
          value={command.id}
          className="min-h-[180px] border-b p-6 md:p-8"
        >
          <TypedCode code={command.code} prompt={command.prompt} />
          <p className="mt-6 text-[13px] leading-[22px] text-muted-foreground">{command.hint}</p>
        </TabsContent>
      ))}
      <div className="flex items-center justify-between gap-4 px-6 py-3 text-xs text-muted-foreground md:px-8">
        <span>Java 25 · Minecraft 26.3</span>
        <button
          type="button"
          onClick={() => void copyActive()}
          className="inline-flex min-h-11 items-center gap-2 rounded-lg border bg-muted px-4 text-xs font-bold transition-colors hover:border-primary hover:text-primary"
        >
          {copied ? (
            <Check aria-hidden className="size-3.5" />
          ) : (
            <Copy aria-hidden className="size-3.5" />
          )}
          {copied ? "Copied" : "Copy"}
        </button>
      </div>
      <p role="status" aria-live="polite" className="sr-only">
        {status &&
          (status.copied
            ? "Command copied to the clipboard."
            : "Copying failed. Select the command and copy it by hand.")}
      </p>
    </Tabs>
  );
}
